package com.memorizer.app;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Local CA generator and leaf certificate issuer for HTTPS (dev/local use).
 * Generates/stores a CA under data/ca/, and issues a PKCS12 leaf for Jetty with proper SAN/EKU/KU.
 */
public final class LocalCAService {
    private static final String BC = BouncyCastleProvider.PROVIDER_NAME;
    private static final Path CA_DIR = Paths.get("data", "ca");
    private static final Path CA_CERT_PEM = CA_DIR.resolve("ca.crt");
    private static final Path CA_STORE_P12 = CA_DIR.resolve("ca.p12");
    private static final Path LEAF_STORE_P12 = CA_DIR.resolve("server.p12");

    static {
        if (Security.getProvider(BC) == null) Security.addProvider(new BouncyCastleProvider());
    }

    public static synchronized void ensureCA() {
        try {
            Files.createDirectories(CA_DIR);
            if (Files.exists(CA_CERT_PEM) && Files.exists(CA_STORE_P12)) return;
            KeyPair caKp = genRSA();
            X500Name issuer = new X500Name("CN=Memorizer Local CA, O=Memorizer");
            X509Certificate caCert = selfSignCA(caKp, issuer, days(3650));
            // write CA cert PEM
            writePemCert(caCert, CA_CERT_PEM);
            // write CA PKCS12 store
            char[] caPass = getCAStorePass();
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, caPass);
            ks.setKeyEntry("ca", caKp.getPrivate(), caPass, new Certificate[]{caCert});
            try (OutputStream out = Files.newOutputStream(CA_STORE_P12)) {
                ks.store(out, caPass);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure local CA", e);
        }
    }

    public static synchronized Path ensureLeafKeystore(String host, String ip) {
        ensureCA();
        try {
            // If existing leaf covers host/ip, reuse
            if (Files.exists(LEAF_STORE_P12)) {
                try {
                    if (leafCovers(host, ip)) return LEAF_STORE_P12;
                } catch (Exception ignored) {}
            }
            // Issue new leaf
            KeyPair leafKp = genRSA();
            X500Name subj = new X500Name("CN=Memorizer Local Server");
            // Load CA keypair and cert
            char[] caPass = getCAStorePass();
            KeyStore caKs = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(CA_STORE_P12)) { caKs.load(in, caPass); }
            PrivateKey caKey = (PrivateKey) caKs.getKey("ca", caPass);
            X509Certificate caCert = (X509Certificate) caKs.getCertificate("ca");
            X509Certificate leaf = signLeaf(leafKp.getPublic(), subj, caKey, caCert, host, ip, days(365));

            // Write server PKCS12 with chain
            char[] leafPass = getLeafStorePass();
            KeyStore leafKs = KeyStore.getInstance("PKCS12");
            leafKs.load(null, leafPass);
            leafKs.setKeyEntry("server", leafKp.getPrivate(), leafPass, new Certificate[]{leaf, caCert});
            Files.createDirectories(LEAF_STORE_P12.getParent());
            try (OutputStream out = Files.newOutputStream(LEAF_STORE_P12)) { leafKs.store(out, leafPass); }
            return LEAF_STORE_P12;
        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure leaf keystore", e);
        }
    }

    public static char[] getLeafStorePass() {
        return Config.get("app.web.keystore.pass", "memorizer-dev-pass").toCharArray();
    }
    public static char[] getCAStorePass() {
        return Config.get("app.web.ca.pass", "memorizer-ca-pass").toCharArray();
    }

    public static Path caCertPath() { return CA_CERT_PEM; }
    public static Path leafStorePath() { return LEAF_STORE_P12; }

    private static boolean leafCovers(String host, String ip) throws Exception {
        char[] pass = getLeafStorePass();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(LEAF_STORE_P12)) { ks.load(in, pass); }
        X509Certificate cert = (X509Certificate) ks.getCertificate("server");
        if (cert == null) return false;
        Collection<List<?>> sans = cert.getSubjectAlternativeNames();
        boolean hasHost = false, hasIp = false;
        if (sans != null) {
            for (List<?> alt : sans) {
                Integer type = (Integer) alt.get(0);
                Object val = alt.get(1);
                if (type == 2) { // dNSName
                    if (host != null && host.equalsIgnoreCase(String.valueOf(val))) hasHost = true;
                    if ("localhost".equalsIgnoreCase(String.valueOf(val))) { /* ok */ }
                } else if (type == 7) { // iPAddress
                    if (ip != null && ip.equals(String.valueOf(val))) hasIp = true;
                }
            }
        }
        // Also ensure validity not expired
        cert.checkValidity(new Date());
        return hasIp || hasHost; // prefer any coverage to avoid churn
    }

    private static KeyPair genRSA() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    private static X509Certificate selfSignCA(KeyPair kp, X500Name issuer, int days) throws Exception {
        Date notBefore = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L);
        Date notAfter = new Date(System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L);
        BigInteger serial = new BigInteger(64, new SecureRandom());
        SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(kp.getPublic().getEncoded());
        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, issuer, spki);
        JcaX509ExtensionUtils ext = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.subjectKeyIdentifier, false, ext.createSubjectKeyIdentifier(kp.getPublic()));
        builder.addExtension(Extension.authorityKeyIdentifier, false, ext.createAuthorityKeyIdentifier(kp.getPublic()));
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider(BC).build(kp.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter().setProvider(BC).getCertificate(holder);
    }

    private static X509Certificate signLeaf(PublicKey pub, X500Name subj, PrivateKey caKey, X509Certificate caCert,
                                            String host, String ip, int days) throws Exception {
        Date notBefore = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L);
        // iOS requires leaf validity <= 825 days; we enforce <= 365 by caller
        Date notAfter = new Date(System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L);
        BigInteger serial = new BigInteger(64, new SecureRandom());
        SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(pub.getEncoded());
        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
                new X500Name(caCert.getSubjectX500Principal().getName()), serial, notBefore, notAfter, subj, spki);
        JcaX509ExtensionUtils ext = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.subjectKeyIdentifier, false, ext.createSubjectKeyIdentifier(pub));
        builder.addExtension(Extension.authorityKeyIdentifier, false, ext.createAuthorityKeyIdentifier(caCert));
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        // KU: digitalSignature + keyEncipherment
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        // EKU: serverAuth
        builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
        // SANs
        List<GeneralName> genNames = new ArrayList<>();
        genNames.add(new GeneralName(GeneralName.dNSName, "localhost"));
        if (host != null && !host.isEmpty()) genNames.add(new GeneralName(GeneralName.dNSName, host));
        if (ip != null && !ip.isEmpty()) genNames.add(new GeneralName(GeneralName.iPAddress, ip));
        GeneralNames sans = GeneralNames.getInstance(new DERSequence(genNames.toArray(new ASN1Encodable[0])));
        builder.addExtension(Extension.subjectAlternativeName, false, sans);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider(BC).build(caKey);
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter().setProvider(BC).getCertificate(holder);
    }

    private static int days(int d) { return d; }

    private static void writePemCert(X509Certificate cert, Path dst) throws IOException {
        String base64;
        try {
            base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(cert.getEncoded());
        } catch (java.security.cert.CertificateEncodingException e) {
            throw new IOException("cert encode", e);
        }
        String pem = "-----BEGIN CERTIFICATE-----\n" + base64 + "\n-----END CERTIFICATE-----\n";
        Files.write(dst, pem.getBytes(StandardCharsets.US_ASCII));
    }

    private LocalCAService() {}
}
