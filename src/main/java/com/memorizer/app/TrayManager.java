package com.memorizer.app;

import com.memorizer.service.StudyService;
import com.memorizer.ui.MainStage;
import com.memorizer.ui.StealthStage;
import javafx.application.Platform;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public final class TrayManager {
	private final TrayIcon trayIcon;
	private final StealthStage stealthStage;
	private final MainStage mainStage;
	private final StudyService study;
	private final Scheduler scheduler;
	private final java.util.concurrent.ScheduledExecutorService planTicker = java.util.concurrent.Executors
			.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "plan-indicator-ticker");
				t.setDaemon(true);
				return t;
			});
	private MenuItem planSummaryItem;
	private MenuItem planNextItem;

	private final MenuItem miPause = new MenuItem("Pause Reminders");
	private final MenuItem miResume = new MenuItem("Resume Reminders");

	public TrayManager(StealthStage stealthStage, MainStage mainStage, StudyService study, Scheduler scheduler) {
		this.stealthStage = stealthStage;
		this.mainStage = mainStage;
		this.study = study;
		this.scheduler = scheduler;

		if (!SystemTray.isSupported())
			throw new IllegalStateException("System tray not supported on this platform.");
		final SystemTray tray = SystemTray.getSystemTray();

		Image image = loadIcon();
		PopupMenu menu = new PopupMenu();

		MenuItem miOpenMain = new MenuItem("Open Main Window");
		// live plan indicators
		planSummaryItem = new MenuItem("Plan: -/-");
		planNextItem = new MenuItem("Next: -");
		MenuItem miShow = new MenuItem("Show Stealth Now");
		MenuItem miSnooze = new MenuItem("Snooze 10 min");
		MenuItem miSkip = new MenuItem("Skip Current (S)");
		Menu mStudyMode = new Menu("Study Mode");
		CheckboxMenuItem miFixed = new CheckboxMenuItem("Fixed");
		CheckboxMenuItem miChallenge = new CheckboxMenuItem("Challenge");
		mStudyMode.add(miFixed);
		mStudyMode.add(miChallenge);
		Menu mPlan = new Menu("Plan");
		MenuItem miRebuild = new MenuItem("Rebuild Today");
		MenuItem miPlanStatus = new MenuItem("Plan Status");
		MenuItem miAppendChallenge = new MenuItem("Append Challenge Batch");
		MenuItem miRoll = new MenuItem("Roll Remaining");
		MenuItem miClearChal = new MenuItem("Clear Challenge");
		mPlan.add(miRebuild);
		mPlan.add(miAppendChallenge);
		mPlan.add(miRoll);
		mPlan.add(miClearChal);
		mPlan.add(miPlanStatus);
		MenuItem miImport = new MenuItem("Import Excel...");
		MenuItem miTemplate = new MenuItem("Save Import Template...");
		MenuItem miH2 = new MenuItem("Open H2 Console");
		MenuItem miExit = new MenuItem("Exit");

		menu.add(miOpenMain);
		menu.add(miShow);
		// Insert UI mode menu right after Show
		Menu modeMenu = new Menu("Mode");
		CheckboxMenuItem miNormal = new CheckboxMenuItem("Normal");
		CheckboxMenuItem miMini = new CheckboxMenuItem("Mini");
		modeMenu.add(miNormal);
		modeMenu.add(miMini);
		menu.add(modeMenu);
		// Theme menu: Dark/Light toggle
		Menu themeMenu = new Menu("Theme");
		CheckboxMenuItem miDark = new CheckboxMenuItem("Dark");
		CheckboxMenuItem miLight = new CheckboxMenuItem("Light");
		themeMenu.add(miDark);
		themeMenu.add(miLight);
		menu.add(themeMenu);
		menu.addSeparator();
		menu.add(mStudyMode);
		menu.add(mPlan);
		menu.add(planSummaryItem);
		menu.add(planNextItem);
		menu.add(miSkip);
		menu.add(miPause);
		menu.add(miResume);
		menu.add(miSnooze);
		menu.addSeparator();
		menu.add(miImport);
		menu.add(miTemplate);
		menu.addSeparator();
		menu.add(miH2);
		menu.addSeparator();
		menu.add(miExit);

		// 读取当前 UI 模式
		boolean mini = "mini".equalsIgnoreCase(com.memorizer.app.Config.get("app.ui.mode", "normal"));
		miMini.setState(mini);
		miNormal.setState(!mini);
		// 读取当前主题
		boolean light = "light".equalsIgnoreCase(com.memorizer.app.Config.get("app.ui.theme", "dark"));
		miLight.setState(light);
		miDark.setState(!light);

		// 互斥选择
		miNormal.addItemListener(e -> {
			if (miNormal.getState()) {
				miMini.setState(false);
				javafx.application.Platform.runLater(() -> {
					stealthStage.setUIMode(com.memorizer.ui.StealthStage.UIMode.NORMAL);
					if (mainStage != null)
						mainStage.refreshModeIndicatorInStudy();
				});
				// 如需持久化，后续加入 Config 持久保存
			} else if (!miMini.getState()) {
				miNormal.setState(true);
			}
		});
		miMini.addItemListener(e -> {
			if (miMini.getState()) {
				miNormal.setState(false);
				javafx.application.Platform.runLater(() -> {
					stealthStage.setUIMode(com.memorizer.ui.StealthStage.UIMode.MINI);
					if (mainStage != null)
						mainStage.refreshModeIndicatorInStudy();
				});
			} else if (!miNormal.getState()) {
				miMini.setState(true);
			}
		});

		// Theme mutual exclusion
		miDark.addItemListener(e -> {
			if (miDark.getState()) {
				miLight.setState(false);
				javafx.application.Platform.runLater(() -> {
					stealthStage.setTheme(com.memorizer.ui.StealthStage.ThemeMode.DARK);
					if (mainStage != null)
						mainStage.applyTheme(false);
				});
			} else if (!miLight.getState()) {
				miDark.setState(true);
			}
		});
		miLight.addItemListener(e -> {
			if (miLight.getState()) {
				miDark.setState(false);
				javafx.application.Platform.runLater(() -> {
					stealthStage.setTheme(com.memorizer.ui.StealthStage.ThemeMode.LIGHT);
					if (mainStage != null)
						mainStage.applyTheme(true);
				});
			} else if (!miDark.getState()) {
				miLight.setState(true);
			}
		});

		// Mode menu already inserted below "Show" above

		trayIcon = new TrayIcon(image, "Memorizer", menu);
		trayIcon.setImageAutoSize(true);
		TrayActions.attachTrayIcon(trayIcon);

		miOpenMain.addActionListener(e -> Platform.runLater(mainStage::showAndFocus));

		miShow.addActionListener(e -> Platform.runLater(() -> {
			TrayActions.showStealthNow(study);
		}));

		miPause.addActionListener(e -> {
			scheduler.pause();
			updatePauseMenu();
		});

		// 学习模式（持久化到 Config）
		boolean challenge = "challenge".equalsIgnoreCase(Config.get("app.study.mode", "fixed"));
		miChallenge.setState(challenge);
		miFixed.setState(!challenge);
		miFixed.addItemListener(e -> {
			if (miFixed.getState()) {
				miChallenge.setState(false);
				Config.set("app.study.mode", "fixed");
			} else if (!miChallenge.getState())
				miFixed.setState(true);
		});
		miChallenge.addItemListener(e -> {
			if (miChallenge.getState()) {
				miFixed.setState(false);
				Config.set("app.study.mode", "challenge");
			} else if (!miFixed.getState())
				miChallenge.setState(true);
		});

		// 计划快捷操作
		miRebuild.addActionListener(e -> {
			study.rebuildTodayPlan();
			updatePlanTooltip();
			updatePlanMenu(planSummaryItem, planNextItem);
		});
		miAppendChallenge.addActionListener(e -> {
			study.appendChallengeBatch(Config.getInt("app.study.challenge-batch-size", 10));
			updatePlanTooltip();
			updatePlanMenu(planSummaryItem, planNextItem);
		});
		miRoll.addActionListener(e -> {
			study.rollRemainingToday();
			updatePlanTooltip();
			updatePlanMenu(planSummaryItem, planNextItem);
		});
		miClearChal.addActionListener(e -> {
			study.clearChallengeToday();
			updatePlanTooltip();
			updatePlanMenu(planSummaryItem, planNextItem);
		});
		miPlanStatus.addActionListener(e -> {
			try {
				com.memorizer.service.PlanService.Counts pc = study.planCounts();
				trayIcon.displayMessage("Plan Status",
						"Pending: " + pc.pending + "\nDone: " + pc.done + "\nTotal: " + pc.total,
						TrayIcon.MessageType.INFO);
			} catch (Exception ex) {
				trayIcon.displayMessage("Plan Status", "Unavailable", TrayIcon.MessageType.INFO);
			}
		});

		miResume.addActionListener(e -> {
			scheduler.resume();
			updatePauseMenu();
		});
		miSkip.addActionListener(e -> Platform.runLater(() -> {
			stealthStage.skipCurrent();
			updatePlanMenu(planSummaryItem, planNextItem);
		}));

		miSnooze.addActionListener(e -> scheduler.snooze(Config.getInt("app.study.snooze-minutes", 10)));

		miImport.addActionListener(e -> TrayActions.openImportDialog());
		miTemplate.addActionListener(e -> TrayActions.saveTemplateDialog());

		miH2.addActionListener(openH2());
		miExit.addActionListener(e -> {
			H2ConsoleServer.stop();
			Platform.runLater(() -> {
				stealthStage.close();
				if (mainStage != null)
					mainStage.close();
			});
			tray.remove(trayIcon);
			// ensure background ticker is stopped to prevent leaks
			shutdown();
			scheduler.stop();
			System.exit(0);
		});

		updatePauseMenu();
		updatePlanTooltip();
		updatePlanMenu(planSummaryItem, planNextItem);
		planTicker.scheduleAtFixedRate(() -> {
			try {
				updatePlanTooltip();
				updatePlanMenu(planSummaryItem, planNextItem);
			} catch (Exception ignored) {
			}
		}, 60, 60, java.util.concurrent.TimeUnit.SECONDS);
		// prime menu labels
		try {
			tray.add(trayIcon);
		} catch (AWTException ex) {
			throw new RuntimeException("Failed to add tray icon", ex);
		}
	}

	/** Stop background executors held by the tray to avoid resource leaks. */
	public void shutdown() {
		try {
			planTicker.shutdownNow();
		} catch (Exception ignored) {
		}
	}

	public void updatePlanTooltip() {
		try {
			com.memorizer.service.PlanService.Counts pc = study.planCounts();
			trayIcon.setToolTip("Memorizer — Plan " + pc.pending + "/" + pc.total);
		} catch (Exception ignored) {
			trayIcon.setToolTip("Memorizer");
		}
	}

	private void updatePauseMenu() {
		boolean paused = scheduler.isPaused();
		miPause.setEnabled(!paused);
		miResume.setEnabled(paused);
	}

	private ActionListener openH2() {
		return e -> {
			H2ConsoleServer.startIfEnabled();
			trayIcon.displayMessage("H2 Console",
					"Open http://127.0.0.1:" + Config.get("app.h2.console.port", "8082") + "/",
					TrayIcon.MessageType.INFO);
		};
	}

	private void updatePlanMenu(MenuItem miPlanSummary, MenuItem miPlanNext) {
		try {
			com.memorizer.service.PlanService.Counts pc = study.planCounts();
			miPlanSummary.setLabel("Plan: " + pc.pending + "/" + pc.total);
			String next = study.previewNextFromPlanFront().orElse("-");
			if (next.length() > 30)
				next = next.substring(0, 30) + "…";
			miPlanNext.setLabel("Next: " + next);
		} catch (Exception ignored) {
			miPlanSummary.setLabel("Plan: -/-");
			miPlanNext.setLabel("Next: -");
		}
	}

	private Image loadIcon() {
		try (InputStream in = TrayManager.class.getResourceAsStream("/icon-16.png")) {
			if (in != null)
				return ImageIO.read(in);
		} catch (IOException ignored) {
		}
		int sz = 16;
		Image img = new BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = (Graphics2D) img.getGraphics();
		g.setColor(new Color(60, 180, 75));
		g.fillOval(0, 0, sz, sz);
		g.dispose();
		return img;
	}
}
