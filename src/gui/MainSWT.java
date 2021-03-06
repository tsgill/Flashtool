package gui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.Deflater;

import javax.swing.JOptionPane;

import libusb.LibUsbException;
import linuxlib.JUsb;
import org.adb.AdbUtility;
import org.adb.FastbootUtility;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.wb.swt.SWTResourceManager;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.logger.MyLogger;
import org.system.AdbPhoneThread;
import org.system.Device;
import org.system.DeviceChangedListener;
import org.system.DeviceEntry;
import org.system.DeviceProperties;
import org.system.Devices;
import org.system.FTDEntry;
import org.system.FTShell;
import org.system.GlobalConfig;
import org.system.OS;
import org.system.StatusEvent;
import org.system.StatusListener;
import org.system.TextFile;
import org.system.ULCodeFile;
import org.system.VersionChecker;
import flashsystem.Bundle;
import flashsystem.TaEntry;
import flashsystem.X10flash;
import gui.tools.APKInstallJob;
import gui.tools.BackupSystemJob;
import gui.tools.BackupTAJob;
import gui.tools.BusyboxInstallJob;
import gui.tools.DecryptJob;
import gui.tools.FTDExplodeJob;
import gui.tools.FlashJob;
import gui.tools.GetULCodeJob;
import gui.tools.MsgBox;
import gui.tools.RawTAJob;
import gui.tools.RootJob;
import gui.tools.WidgetTask;
import gui.tools.WidgetsTool;
import org.eclipse.swt.custom.ScrolledComposite;

public class MainSWT {

	protected Shell shlSonyericsson;
	private static AdbPhoneThread phoneWatchdog;
	public static boolean guimode=false;
	protected ToolItem tltmFlash;
	protected ToolItem tltmRoot;
	protected ToolItem tltmAskRoot;
	protected ToolItem tltmBLU;
	protected ToolItem tltmApkInstall;
	protected MenuItem mntmSwitchPro;
	protected MenuItem mntmAdvanced;
	protected MenuItem mntmNoDevice;
	protected MenuItem mntmInstallBusybox;
	protected MenuItem mntmRawBackup;
	protected MenuItem mntmRawRestore;
	protected VersionChecker vcheck=null;
	
	/**
	 * Open the window.
	 */
	public void open() {
		Display.setAppName("Flashtool");
		Display display = Display.getDefault();
		createContents();
		WidgetsTool.setSize(shlSonyericsson);
		guimode=true;
		StatusListener phoneStatus = new StatusListener() {
			public void statusChanged(StatusEvent e) {
				if (!e.isDriverOk()) {
					MyLogger.getLogger().error("Drivers need to be installed for connected device.");
					MyLogger.getLogger().error("You can find them in the drivers folder of Flashtool.");
				}
				else {
					if (e.getNew().equals("adb")) {
						MyLogger.getLogger().info("Device connected with USB debugging on");
						MyLogger.getLogger().debug("Device connected, continuing with identification");
						doIdent();
					}
					if (e.getNew().equals("none")) {
						MyLogger.getLogger().info("Device disconnected");
						doDisableIdent();
					}
					if (e.getNew().equals("flash")) {
						MyLogger.getLogger().info("Device connected in flash mode");
						doDisableIdent();
					}
					if (e.getNew().equals("fastboot")) {
						MyLogger.getLogger().info("Device connected in fastboot mode");
						doDisableIdent();
					}
					if (e.getNew().equals("normal")) {
						MyLogger.getLogger().info("Device connected with USB debugging off");
						MyLogger.getLogger().info("For 2011 devices line, be sure you are not in MTP mode");
						doDisableIdent();
					}
				}
			}
		};
		killAdbandFastboot();
		phoneWatchdog = new AdbPhoneThread();
		phoneWatchdog.start();
		phoneWatchdog.addStatusListener(phoneStatus);
		vcheck = new VersionChecker();
		vcheck.setMessageFrame(shlSonyericsson);
		vcheck.start();
		shlSonyericsson.open();
		shlSonyericsson.layout();
		try {
			Main.initLinuxUsb();
		}
		catch (LibUsbException e) {
			shlSonyericsson.dispose();
		}
		while (!shlSonyericsson.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	public void doDisableIdent() {
		WidgetTask.setEnabled(tltmFlash,true);
		WidgetTask.setEnabled(tltmRoot,false);
		WidgetTask.setEnabled(tltmAskRoot,false);
		WidgetTask.setEnabled(tltmApkInstall,false);
		WidgetTask.setMenuName(mntmNoDevice, "No Device");
		WidgetTask.setEnabled(mntmNoDevice,false);
		WidgetTask.setEnabled(mntmRawRestore,false);
		WidgetTask.setEnabled(mntmRawBackup,false);
	}
	
	/**
	 * Create contents of the window.
	 * @wbp.parser.entryPoint
	 */
	protected void createContents() {
		shlSonyericsson = new Shell();
		shlSonyericsson.addListener(SWT.Close, new Listener() {
		      public void handleEvent(Event event) {
		    	  exitProgram();
		      }
		    });
		shlSonyericsson.setSize(794, 451);
		shlSonyericsson.setText("SonyEricsson Xperia Flasher by Bin4ry & Androxyde");
		shlSonyericsson.setImage(SWTResourceManager.getImage(MainSWT.class, "/gui/ressources/icons/flash_32.png"));
		shlSonyericsson.setLayout(new FormLayout());
		
		Menu menu = new Menu(shlSonyericsson, SWT.BAR);
		shlSonyericsson.setMenuBar(menu);
		
		MenuItem mntmFile = new MenuItem(menu, SWT.CASCADE);
		mntmFile.setText("File");
		
		Menu menu_1 = new Menu(mntmFile);
		mntmFile.setMenu(menu_1);
		
		mntmSwitchPro = new MenuItem(menu_1, SWT.NONE);
		mntmSwitchPro.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean ispro = GlobalConfig.getProperty("devfeatures").equals("yes");
    			GlobalConfig.setProperty("devfeatures", ispro?"no":"yes");
    			mntmAdvanced.setEnabled(!ispro);
    			mntmSwitchPro.setText(!ispro?"Switch Simple":"Switch Pro");
    			//mnDev.setVisible(!ispro);
    			//mntmSwitchPro.setText(Language.getMessage(mntmSwitchPro.getName()));
    		    //mnDev.setText(Language.getMessage(mnDev.getName()));
			}
		});
		mntmSwitchPro.setText(GlobalConfig.getProperty("devfeatures").equals("yes")?"Switch Simple":"Switch Pro");
		
		MenuItem mntmExit = new MenuItem(menu_1, SWT.NONE);
		mntmExit.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				exitProgram();
				shlSonyericsson.dispose();
			}
		});
		mntmExit.setText("Exit");
		
		mntmNoDevice = new MenuItem(menu, SWT.CASCADE);
		mntmNoDevice.setText("No Device");
		mntmNoDevice.setEnabled(false);
		
		Menu menu_8 = new Menu(mntmNoDevice);
		mntmNoDevice.setMenu(menu_8);
		
		MenuItem mntmRoot = new MenuItem(menu_8, SWT.CASCADE);
		mntmRoot.setText("Root");
		
		Menu menu_10 = new Menu(mntmRoot);
		mntmRoot.setMenu(menu_10);
		
		MenuItem mntmForcePsneuter = new MenuItem(menu_10, SWT.NONE);
		mntmForcePsneuter.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doRoot("doRootpsneuter");
			}
		});
		mntmForcePsneuter.setText("Force PsNeuter");
		
		MenuItem mntmForceZergrush = new MenuItem(menu_10, SWT.NONE);
		mntmForceZergrush.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doRoot("doRootzergRush");
			}
		});
		mntmForceZergrush.setText("Force zergRush");
		
		MenuItem mntmForceEmulator = new MenuItem(menu_10, SWT.NONE);
		mntmForceEmulator.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doRoot("doRootEmulator");
			}
		});
		mntmForceEmulator.setText("Force Emulator");
		
		MenuItem mntmForceAdbrestore = new MenuItem(menu_10, SWT.NONE);
		mntmForceAdbrestore.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doRoot("doRootAdbRestore");
			}
		});
		mntmForceAdbrestore.setText("Force AdbRestore");
		
		MenuItem mntmForceServicemenu = new MenuItem(menu_10, SWT.NONE);
		mntmForceServicemenu.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doRoot("doRootServiceMenu");
			}
		});
		mntmForceServicemenu.setText("Force ServiceMenu");
		
		MenuItem mntmBackupSystemApps = new MenuItem(menu_8, SWT.NONE);
		mntmBackupSystemApps.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				BackupSystemJob bsj = new BackupSystemJob("Backup System apps");
				bsj.schedule();
			}
		});
		mntmBackupSystemApps.setText("Backup system apps");
		
		mntmInstallBusybox = new MenuItem(menu_8, SWT.NONE);
		mntmInstallBusybox.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
        		String busybox = Devices.getCurrent().getBusybox(true);
        		if (busybox.length()>0) {
        			BusyboxInstallJob bij = new BusyboxInstallJob("Busybox Install");
        			bij.setBusybox(busybox);
        			bij.schedule();
        		}
			}
		});
		mntmInstallBusybox.setText("Install busybox");
		
		MenuItem mntmReboot = new MenuItem(menu_8, SWT.NONE);
		mntmReboot.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					Devices.getCurrent().reboot();
				}
				catch (Exception ex) {
				}
			}
		});
		mntmReboot.setText("Reboot");
		
		MenuItem mntmNewSubmenu = new MenuItem(menu, SWT.CASCADE);
		mntmNewSubmenu.setText("Tools");
		
		Menu menu_4 = new Menu(mntmNewSubmenu);
		mntmNewSubmenu.setMenu(menu_4);
		
		MenuItem mntmNewItem = new MenuItem(menu_4, SWT.NONE);
		mntmNewItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				SinEditor sedit = new SinEditor(shlSonyericsson,SWT.PRIMARY_MODAL | SWT.SHEET);
				sedit.open();
			}
		});
		mntmNewItem.setText("Sin Editor");
		
		MenuItem mntmExtractors = new MenuItem(menu_4, SWT.CASCADE);
		mntmExtractors.setText("Extractors");
		
		Menu menu_5 = new Menu(mntmExtractors);
		mntmExtractors.setMenu(menu_5);
		
		MenuItem mntmElf = new MenuItem(menu_5, SWT.NONE);
		mntmElf.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ElfEditor elfedit = new ElfEditor(shlSonyericsson,SWT.PRIMARY_MODAL | SWT.SHEET);
				elfedit.open();
			}
		});
		mntmElf.setText("ELF");
		
		MenuItem mntmNewItem_1 = new MenuItem(menu_4, SWT.NONE);
		mntmNewItem_1.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Decrypt decrypt = new Decrypt(shlSonyericsson,SWT.PRIMARY_MODAL | SWT.SHEET);
				Vector result = decrypt.open();
				if (result!=null) {
					File f = (File)result.get(0);
					final String folder = f.getParent();
					DecryptJob dec = new DecryptJob("Decrypt");
					dec.addJobChangeListener(new IJobChangeListener() {
						public void aboutToRun(IJobChangeEvent event) {
						}

						public void awake(IJobChangeEvent event) {
						}

						public void done(IJobChangeEvent event) {
				    		//BundleCreator cre = new BundleCreator(shlSonyericsson,SWT.PRIMARY_MODAL | SWT.SHEET);
				    		//String result = (String)cre.open(folder);
							String result = WidgetTask.openBundleCreator(shlSonyericsson,folder);
							if (result.equals("Cancel"))
								MyLogger.getLogger().info("Bundle creation canceled");
						}

						public void running(IJobChangeEvent event) {
						}

						public void scheduled(IJobChangeEvent event) {
						}

						public void sleeping(IJobChangeEvent event) {
						}
					});

					dec.setFiles(result);
					dec.schedule();
				}
				else {
					MyLogger.getLogger().info("Decrypt canceled");
				}
			}
		});
		mntmNewItem_1.setText("SEUS Decrypt");
		
		MenuItem mntmBundleCreation = new MenuItem(menu_4, SWT.NONE);
		mntmBundleCreation.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				BundleCreator cre = new BundleCreator(shlSonyericsson,SWT.PRIMARY_MODAL | SWT.SHEET);
				String result = (String)cre.open();
				if (result.equals("Cancel"))
					MyLogger.getLogger().info("Bundle creation canceled");
			}
		});
		mntmBundleCreation.setText("Bundle Creation");
		
		mntmAdvanced = new MenuItem(menu, SWT.CASCADE);
		mntmAdvanced.setText("Advanced");
		
		
		Menu AdvancedMenu = new Menu(mntmAdvanced);
		
		mntmAdvanced.setMenu(AdvancedMenu);
		
		MenuItem mntmTrimArea = new MenuItem(AdvancedMenu, SWT.CASCADE);
		mntmTrimArea.setText("Trim Area");
		
		Menu menu_9 = new Menu(mntmTrimArea);
		mntmTrimArea.setMenu(menu_9);
		
		MenuItem mntmBackup = new MenuItem(menu_9, SWT.NONE);
		mntmBackup.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doBackupTA();
			}
		});
		mntmBackup.setText("Backup");
		
		mntmRawBackup = new MenuItem(menu_9, SWT.NONE);
		mntmRawBackup.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				RawTAJob rj = new RawTAJob("Raw TA");
				rj.setAction("doBackup");
				rj.setShell(shlSonyericsson);
				rj.schedule();
			}
		});
		mntmRawBackup.setText("Raw backup");
		mntmRawBackup.setEnabled(false);
		
		mntmRawRestore = new MenuItem(menu_9, SWT.NONE);
		mntmRawRestore.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				RawTAJob rj = new RawTAJob("Raw TA");
				rj.setAction("doRestore");
				rj.setShell(shlSonyericsson);
				rj.schedule();
			}
		});
		mntmRawRestore.setText("Raw Restore");
		mntmRawRestore.setEnabled(false);
		mntmAdvanced.setEnabled(GlobalConfig.getProperty("devfeatures").equals("yes"));
		MenuItem mntmDevices = new MenuItem(menu, SWT.CASCADE);
		mntmDevices.setText("Devices");
		
		Menu menu_6 = new Menu(mntmDevices);
		mntmDevices.setMenu(menu_6);
		
		MenuItem mntmCheckDrivers = new MenuItem(menu_6, SWT.NONE);
		mntmCheckDrivers.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Device.CheckAdbDrivers();
			}
		});
		mntmCheckDrivers.setText("Check drivers");
		
		MenuItem mntmEditor = new MenuItem(menu_6, SWT.CASCADE);
		mntmEditor.setText("Manage");
		
		Menu menu_7 = new Menu(mntmEditor);
		mntmEditor.setMenu(menu_7);
		
		//MenuItem mntmEdit = new MenuItem(menu_7, SWT.NONE);
		//mntmEdit.setText("Edit");
		
		//MenuItem mntmAdd = new MenuItem(menu_7, SWT.NONE);
		//mntmAdd.setText("Add");
		
		//MenuItem mntmRemove = new MenuItem(menu_7, SWT.NONE);
		//mntmRemove.setText("Remove");
		
		MenuItem mntmExport = new MenuItem(menu_7, SWT.NONE);
		mntmExport.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Devices.listDevices(true);
				String devid = WidgetTask.openDeviceSelector(shlSonyericsson);
				DeviceEntry ent = Devices.getDevice(devid);
        		if (devid.length()>0) {
        			try {
        				MyLogger.getLogger().info("Beginning export of "+ent.getName());
        				doExportDevice(devid);
        				MyLogger.getLogger().info(ent.getName()+" exported successfully");
        			}
        			catch (Exception ex) {
        				MyLogger.getLogger().error(ex.getMessage());
        			}
        		}
			}
		});
		mntmExport.setText("Export");
		
		MenuItem mntmImport = new MenuItem(menu_7, SWT.NONE);
		mntmImport.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Devices.listDevices(true);
        		Properties list = new Properties();
        		File[] lfiles = new File(OS.getWorkDir()+File.separator+"devices").listFiles();
        		for (int i=0;i<lfiles.length;i++) {
        			if (lfiles[i].getName().endsWith(".ftd")) {
        				String name = lfiles[i].getName();
        				name = name.substring(0,name.length()-4);        				
        				try {
        					FTDEntry entry = new FTDEntry(name);
        					list.setProperty(entry.getId(), entry.getName());
        				} catch (Exception ex) {ex.printStackTrace();}
        			}
        		}
        		if (list.size()>0) {
        			String devid = WidgetTask.openDeviceSelector(shlSonyericsson,list);
	        		if (devid.length()>0) {
						try {
							FTDEntry entry = new FTDEntry(devid);
							MsgBox.setCurrentShell(shlSonyericsson);
							FTDExplodeJob j = new FTDExplodeJob("FTD Explode job");
							j.setFTD(entry);
							j.schedule();
						}
						catch (Exception ex) {
							MyLogger.getLogger().error(ex.getMessage());
						}
	        		}
	        		else {
	        			MyLogger.getLogger().info("Import canceled");
	        		}
        		}
        		else {
        			MsgBox.error("No device to import");
        		}
			}
		});
		mntmImport.setText("Import");
		
		MenuItem mntmHelp = new MenuItem(menu, SWT.CASCADE);
		mntmHelp.setText("Help");
		
		Menu menu_2 = new Menu(mntmHelp);
		mntmHelp.setMenu(menu_2);
		
		MenuItem mntmLogLevel = new MenuItem(menu_2, SWT.CASCADE);
		mntmLogLevel.setText("Log level");
		
		Menu menu_3 = new Menu(mntmLogLevel);
		mntmLogLevel.setMenu(menu_3);
		
		MenuItem mntmError = new MenuItem(menu_3, SWT.RADIO);
		mntmError.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				MyLogger.setLevel("ERROR");
				GlobalConfig.setProperty("loglevel", "error");
			}
		});
		mntmError.setText("error");
		
		MenuItem mntmWarning = new MenuItem(menu_3, SWT.RADIO);
		mntmWarning.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				MyLogger.setLevel("WARN");
				GlobalConfig.setProperty("loglevel", "warn");
			}
		});
		mntmWarning.setText("warning");
		
		MenuItem mntmInfo = new MenuItem(menu_3, SWT.RADIO);
		mntmInfo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				MyLogger.setLevel("INFO");
				GlobalConfig.setProperty("loglevel", "info");
			}
		});
		mntmInfo.setText("info");
		
		MenuItem mntmDebug = new MenuItem(menu_3, SWT.RADIO);
		mntmDebug.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				MyLogger.setLevel("DEBUG");
				GlobalConfig.setProperty("loglevel", "debug");
			}
		});
		mntmDebug.setText("debug");
		
		MenuItem mntmAbout = new MenuItem(menu_2, SWT.NONE);
		mntmAbout.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				About about = new About(shlSonyericsson,SWT.PRIMARY_MODAL | SWT.SHEET);
				about.open();
			}
		});
		mntmAbout.setText("About");

		if (GlobalConfig.getProperty("loglevel").equals("debug"))
			mntmDebug.setSelection(true);
		if (GlobalConfig.getProperty("loglevel").equals("warn"))
			mntmWarning.setSelection(true);
		if (GlobalConfig.getProperty("loglevel").equals("info"))
			mntmInfo.setSelection(true);
		if (GlobalConfig.getProperty("loglevel").equals("error"))
			mntmError.setSelection(true);

		ToolBar toolBar = new ToolBar(shlSonyericsson, SWT.FLAT | SWT.RIGHT);
		FormData fd_toolBar = new FormData();
		fd_toolBar.right = new FormAttachment(0, 205);
		fd_toolBar.top = new FormAttachment(0, 10);
		fd_toolBar.left = new FormAttachment(0, 10);
		toolBar.setLayoutData(fd_toolBar);
		
		tltmFlash = new ToolItem(toolBar, SWT.NONE);
		tltmFlash.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					doFlash();
				} catch (Exception ex) {}
			}
		});
		tltmFlash.setImage(SWTResourceManager.getImage(MainSWT.class, "/gui/ressources/icons/flash_32.png"));
		tltmFlash.setToolTipText("Flash device");
		
		tltmBLU = new ToolItem(toolBar, SWT.NONE);
		tltmBLU.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doBLUnlock();
			}
		});
		tltmBLU.setToolTipText("Bootloader Unlock");
		tltmBLU.setImage(SWTResourceManager.getImage(MainSWT.class, "/gui/ressources/icons/blu_32.png"));
		
		tltmRoot = new ToolItem(toolBar, SWT.NONE);
		tltmRoot.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doRoot();
			}
		});
		tltmRoot.setImage(SWTResourceManager.getImage(MainSWT.class, "/gui/ressources/icons/root_32.png"));
		tltmRoot.setEnabled(false);
		tltmRoot.setToolTipText("Root device");
		
		Button btnSaveLog = new Button(shlSonyericsson, SWT.NONE);
		FormData fd_btnSaveLog = new FormData();
		fd_btnSaveLog.right = new FormAttachment(100, -10);
		fd_btnSaveLog.left = new FormAttachment(100, -95);
		btnSaveLog.setLayoutData(fd_btnSaveLog);
		btnSaveLog.setText("Save log");
		
		tltmAskRoot = new ToolItem(toolBar, SWT.NONE);
		tltmAskRoot.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doAskRoot();
			}
		});
		tltmAskRoot.setImage(SWTResourceManager.getImage(MainSWT.class, "/gui/ressources/icons/askroot_32.png"));
		tltmAskRoot.setEnabled(false);
		tltmAskRoot.setToolTipText("Ask for root permissions");
		
		tltmApkInstall = new ToolItem(toolBar, SWT.NONE);
		tltmApkInstall.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ApkInstaller inst = new ApkInstaller(shlSonyericsson,SWT.PRIMARY_MODAL | SWT.SHEET);
				String folder = inst.open();
				if (folder.length()>0) {
					APKInstallJob aij = new APKInstallJob("APK Install");
					aij.setFolder(folder);
					aij.schedule();
				}
				else {
					MyLogger.getLogger().info("Install APK canceled");
				}
			}
		});
		tltmApkInstall.setEnabled(false);
		tltmApkInstall.setImage(SWTResourceManager.getImage(MainSWT.class, "/gui/ressources/icons/customize_32.png"));
		
		ProgressBar progressBar = new ProgressBar(shlSonyericsson, SWT.NONE);
		fd_btnSaveLog.bottom = new FormAttachment(100, -43);
		progressBar.setState(SWT.NORMAL);
		MyLogger.registerProgressBar(progressBar);
		FormData fd_progressBar = new FormData();
		fd_progressBar.left = new FormAttachment(0, 10);
		fd_progressBar.right = new FormAttachment(100, -10);
		fd_progressBar.top = new FormAttachment(btnSaveLog, 6);
		progressBar.setLayoutData(fd_progressBar);
		
		ScrolledComposite scrolledComposite = new ScrolledComposite(shlSonyericsson, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
		FormData fd_scrolledComposite = new FormData();
		fd_scrolledComposite.bottom = new FormAttachment(btnSaveLog, -6);
		fd_scrolledComposite.left = new FormAttachment(0, 10);
		fd_scrolledComposite.right = new FormAttachment(100, -10);
		scrolledComposite.setLayoutData(fd_scrolledComposite);
		scrolledComposite.setExpandHorizontal(true);
		scrolledComposite.setExpandVertical(true);
		
		StyledText logWindow = new StyledText(scrolledComposite, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
		logWindow.setEditable(false);
		MyLogger.appendTextArea(logWindow);
		scrolledComposite.setContent(logWindow);
		scrolledComposite.setMinSize(logWindow.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		
		ToolBar toolBar_1 = new ToolBar(shlSonyericsson, SWT.FLAT | SWT.RIGHT);
		fd_scrolledComposite.top = new FormAttachment(toolBar_1, 2);
		FormData fd_toolBar_1 = new FormData();
		fd_toolBar_1.top = new FormAttachment(0, 10);
		fd_toolBar_1.right = new FormAttachment(btnSaveLog, 0, SWT.RIGHT);
		toolBar_1.setLayoutData(fd_toolBar_1);
		
		ToolItem tltmNewItem = new ToolItem(toolBar_1, SWT.NONE);
		tltmNewItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Program.launch("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=PPWH7M9MNCEPA");
			}
		});
		tltmNewItem.setImage(SWTResourceManager.getImage(MainSWT.class, "/gui/ressources/icons/paypal.png"));
		MyLogger.setLevel(GlobalConfig.getProperty("loglevel").toUpperCase());
/*		try {
		Language.Init(GlobalConfig.getProperty("language").toLowerCase());
		} catch (Exception e) {
			MyLogger.getLogger().info("Language files not installed");
		}*/
		MyLogger.getLogger().info("Flashtool "+About.getVersion());
		if (JUsb.version.length()>0)
			MyLogger.getLogger().info(JUsb.version);
	}

	public static void stopPhoneWatchdog() {
		DeviceChangedListener.stop();
		if (phoneWatchdog!=null) {
			phoneWatchdog.done();
			try {
				phoneWatchdog.join();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void killAdbandFastboot() {
		stopPhoneWatchdog();
	}

	public void exitProgram() {
		try {
			MyLogger.getLogger().info("Stopping watchdogs and exiting ...");
			if (GlobalConfig.getProperty("killadbonexit").equals("yes")) {
				killAdbandFastboot();
			}
			vcheck.done();
			try {
				vcheck.join();
			}
			catch (InterruptedException e) {}
		}
		catch (Exception e) {}		
	}

	public void doIdent() {
    	if (guimode) {
    		Enumeration<Object> e = Devices.listDevices(true);
    		if (!e.hasMoreElements()) {
    			MyLogger.getLogger().error("No device is registered in Flashtool.");
    			MyLogger.getLogger().error("You can only flash devices.");
    			return;
    		}
    		boolean found = false;
    		Properties founditems = new Properties();
    		founditems.clear();
    		Properties buildprop = new Properties();
    		buildprop.clear();
    		while (e.hasMoreElements()) {
    			DeviceEntry current = Devices.getDevice((String)e.nextElement());
    			String prop = current.getBuildProp();
    			if (!buildprop.containsKey(prop)) {
    				String readprop = DeviceProperties.getProperty(prop);
    				buildprop.setProperty(prop,readprop);
    			}
    			Iterator<String> i = current.getRecognitionList().iterator();
    			String localdev = buildprop.getProperty(prop);
    			while (i.hasNext()) {
    				String pattern = i.next().toUpperCase();
    				if (localdev.toUpperCase().equals(pattern)) {
    					founditems.put(current.getId(), current.getName());
    				}
    			}
    		}
    		if (founditems.size()==1) {
    			found = true;
    			Devices.setCurrent((String)founditems.keys().nextElement());
    			if (!Devices.isWaitingForReboot())
    				MyLogger.getLogger().info("Connected device : " + Devices.getCurrent().getName());
    		}
    		else {
    			MyLogger.getLogger().error("Cannot identify your device.");
        		MyLogger.getLogger().info("Selecting from user input");
        		String devid=(String)WidgetTask.openDeviceSelector(shlSonyericsson);
        		//deviceSelectGui devsel = new deviceSelectGui(null);
        		//devid = devsel.getDeviceFromList(founditems);
    			if (devid.length()>0) {
        			found = true;
        			Devices.setCurrent(devid);
        			String prop = DeviceProperties.getProperty(Devices.getCurrent().getBuildProp());
        			if (!Devices.getCurrent().getRecognition().contains(prop)) {
        				String[] choices = {"Yes", "No"};
        				MessageBox messageBox = new MessageBox(shlSonyericsson, SWT.ICON_QUESTION |SWT.YES | SWT.NO);
        			    messageBox.setMessage("Do you want to permanently identify this device as \n"+Devices.getCurrent().getName()+"?");
        			    int response = messageBox.open();
        				if (response == SWT.YES)
        					Devices.getCurrent().addRecognitionToList(prop);
        			}
	        		if (!Devices.isWaitingForReboot())
	        			MyLogger.getLogger().info("Connected device : " + Devices.getCurrent().getId());
        		}
        		else {
        			MyLogger.getLogger().error("You can only flash devices.");
        		}
    		}
    		if (found) {
    			WidgetTask.setEnabled(mntmNoDevice, true);
    			WidgetTask.setMenuName(mntmNoDevice, "My "+Devices.getCurrent().getId());
    			WidgetTask.setEnabled(mntmInstallBusybox,false);
    			if (!Devices.isWaitingForReboot()) {
    				MyLogger.getLogger().info("Installed version of busybox : " + Devices.getCurrent().getInstalledBusyboxVersion(false));
    				MyLogger.getLogger().info("Android version : "+Devices.getCurrent().getVersion()+" / kernel version : "+Devices.getCurrent().getKernelVersion()+" / Build number : "+Devices.getCurrent().getBuildId());
    			}
    			if (Devices.getCurrent().isRecovery()) {
    				MyLogger.getLogger().info("Phone in recovery mode");
    				WidgetTask.setEnabled(tltmRoot,false);
    				WidgetTask.setEnabled(tltmAskRoot,false);
    				WidgetTask.setEnabled(tltmApkInstall,false);
    				doGiveRoot();
    			}
    			else {
    				boolean hasSU = Devices.getCurrent().hasSU();
    				WidgetTask.setEnabled(tltmRoot, !hasSU);
    				WidgetTask.setEnabled(tltmApkInstall, true);
    				if (hasSU) {
    					boolean hasRoot = Devices.getCurrent().hasRoot();
    					if (hasRoot) {
    						doInstFlashtool();
    						doGiveRoot();
    					}
    					WidgetTask.setEnabled(tltmAskRoot,!hasRoot);
    					WidgetTask.setEnabled(mntmInstallBusybox,hasRoot);
    					WidgetTask.setEnabled(mntmRawRestore,hasRoot);
    					WidgetTask.setEnabled(mntmRawBackup,hasRoot);
    				}
    			}
    			MyLogger.getLogger().debug("Now setting buttons availability - btnRoot");
    			MyLogger.getLogger().debug("mtmRootzergRush menu");
    			/*mntmRootzergRush.setEnabled(true);
    			MyLogger.getLogger().debug("mtmRootPsneuter menu");
    			mntmRootPsneuter.setEnabled(true);
    			MyLogger.getLogger().debug("mtmRootEmulator menu");
    			mntmRootEmulator.setEnabled(true);
    			MyLogger.getLogger().debug("mtmRootAdbRestore menu");
    			mntmRootAdbRestore.setEnabled(true);
    			MyLogger.getLogger().debug("mtmUnRoot menu");
    			mntmUnRoot.setEnabled(true);*/

    			boolean flash = Devices.getCurrent().canFlash();
    			MyLogger.getLogger().debug("flashBtn button "+flash);
    			WidgetTask.setEnabled(tltmFlash,flash);
    			//MyLogger.getLogger().debug("custBtn button");
    			//custBtn.setEnabled(true);
    			MyLogger.getLogger().debug("Now adding plugins");
    			//mnPlugins.removeAll();
    			//addDevicesPlugins();
    			//addGenericPlugins();
    			MyLogger.getLogger().debug("Stop waiting for device");
    			if (Devices.isWaitingForReboot())
    				Devices.stopWaitForReboot();
    			MyLogger.getLogger().debug("End of identification");
    		}
    	}
    	File f = new File(OS.getWorkDir()+File.separator+"custom"+File.separator+"apps_saved"+File.separator+Devices.getCurrent().getId());
    	f.mkdir();
    	f = new File(OS.getWorkDir()+File.separator+"custom"+File.separator+"clean"+File.separator+Devices.getCurrent().getId());
    	f.mkdir();
	}

	public void doGiveRoot() {
		/*btnCleanroot.setEnabled(true);
		mntmInstallBusybox.setEnabled(true);
		mntmClearCache.setEnabled(true);
		mntmBuildpropEditor.setEnabled(true);
		if (new File(OS.getWorkDir()+fsep+"devices"+fsep+Devices.getCurrent().getId()+fsep+"rebrand").isDirectory())
			mntmBuildpropRebrand.setEnabled(true);
		mntmRebootIntoRecoveryT.setEnabled(Devices.getCurrent().canRecovery());
		mntmRebootDefaultRecovery.setEnabled(true);
		mntmSetDefaultRecovery.setEnabled(Devices.getCurrent().canRecovery());
		mntmSetDefaultKernel.setEnabled(Devices.getCurrent().canKernel());
		mntmRebootCustomKernel.setEnabled(Devices.getCurrent().canKernel());
		mntmRebootDefaultKernel.setEnabled(true);
		//mntmInstallBootkit.setEnabled(true);
		//mntmRecoveryControler.setEnabled(true);
		mntmBackupSystemApps.setEnabled(true);
		btnXrecovery.setEnabled(Devices.getCurrent().canRecovery());
		btnKernel.setEnabled(Devices.getCurrent().canKernel());*/
		if (!Devices.isWaitingForReboot())
			MyLogger.getLogger().info("Root Access Allowed");  	
    }

	public void doAskRoot() {
		Job job = new Job("Give Root") {
			protected IStatus run(IProgressMonitor monitor) {
				MyLogger.getLogger().warn("Please check your Phone and 'ALLOW' Superuseraccess!");
        		if (!AdbUtility.hasRootPerms()) {
        			MyLogger.getLogger().error("Please Accept root permissions on the phone");
        		}
        		else {
        			doGiveRoot();
        		}
        		return Status.OK_STATUS;				
			}
		};
		job.schedule();
	}
    
	public void doInstFlashtool() {
		try {
			if (!AdbUtility.exists("/system/flashtool")) {
				Devices.getCurrent().doBusyboxHelper();
				MyLogger.getLogger().info("Installing toolbox to device...");
				AdbUtility.push(OS.getWorkDir()+File.separator+"custom"+File.separator+"root"+File.separator+"ftkit.tar",GlobalConfig.getProperty("deviceworkdir"));
				FTShell ftshell = new FTShell("installftkit");
				ftshell.runRoot();
			}
		}
		catch (Exception e) {
			MyLogger.getLogger().error(e.getMessage());
		}
    }

	public void doFlash() throws Exception {
		String select = WidgetTask.openBootModeSelector(shlSonyericsson);
		if (select.equals("flashmode")) {
			doFlashmode("","");
		}
		else if (select.equals("fastboot"))
			doFastBoot();
		else
			MyLogger.getLogger().info("Flash canceled");
	}
	
	public void doFastBoot() throws Exception {
		FastbootToolbox fbbox = new FastbootToolbox(shlSonyericsson,SWT.PRIMARY_MODAL | SWT.SHEET);
		fbbox.open();
	}
	
	public void doFlashmode(final String pftfpath, final String pftfname) throws Exception {
		try {
			FTFSelector ftfsel = new FTFSelector(shlSonyericsson,SWT.PRIMARY_MODAL | SWT.SHEET);
			final Bundle bundle = (Bundle)ftfsel.open(pftfpath, pftfname);
			MyLogger.getLogger().info("Selected "+bundle);
			if (bundle !=null) {
				bundle.setSimulate(GlobalConfig.getProperty("simulate").toLowerCase().equals("yes"));
				final X10flash flash = new X10flash(bundle,shlSonyericsson);
				try {
						FlashJob fjob = new FlashJob("Flash");
						fjob.setFlash(flash);
						fjob.setShell(shlSonyericsson);
						fjob.schedule();
				}
				catch (Exception e){
					MyLogger.getLogger().error(e.getMessage());
					MyLogger.getLogger().info("Flash canceled");
					if (flash.getBundle()!=null)
						flash.getBundle().close();
				}
			}
			else
				MyLogger.getLogger().info("Flash canceled");

		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		

		
		/*Worker.post(new Job() {
			public Object run() {
				System.out.println("flashmode");
				if (bundle!=null) {
					X10flash flash=null;
					try {
			    		MyLogger.getLogger().info("Preparing files for flashing");
			    		bundle.open();
				    	bundle.setSimulate(GlobalConfig.getProperty("simulate").toLowerCase().equals("yes"));
						flash = new X10flash(bundle);
						
						/*if ((new WaitDeviceFlashmodeGUI(flash)).deviceFound(_root)) {
				    		try {
								flash.openDevice();
								flash.flashDevice();
				    		}
				    		catch (Exception e) {
				    			e.printStackTrace();
				    		}
						}
					}
					catch (BundleException ioe) {
						MyLogger.getLogger().error("Error preparing files");
					}
					catch (Exception e) {
						MyLogger.getLogger().error(e.getMessage());
					}
					bundle.close();
				}
				else MyLogger.getLogger().info("Flash canceled");
				return null;
			}
		});*/
	}

	public void doBLUnlock() {
		try {
			final X10flash flash = new X10flash(new Bundle(),shlSonyericsson);
			MyLogger.getLogger().info("Please connect your device into flashmode.");
			String result = (String)WidgetTask.openWaitDeviceForFlashmode(shlSonyericsson,flash);
			if (result.equals("OK")) {
			try {
				GetULCodeJob ulj = new GetULCodeJob("Unlock code");
				ulj.setFlash(flash);
				ulj.addJobChangeListener(new IJobChangeListener() {
					public void aboutToRun(IJobChangeEvent event) {}
					public void awake(IJobChangeEvent event) {}
					public void running(IJobChangeEvent event) {}
					public void scheduled(IJobChangeEvent event) {}
					public void sleeping(IJobChangeEvent event) {}
					
					public void done(IJobChangeEvent event) {
						GetULCodeJob j = (GetULCodeJob)event.getJob();
						String ulcode=j.getULCode();
						String imei = j.getIMEI();
						String blstatus = j.getBLStatus();
						String serial = j.getSerial();
						if (!j.alreadyUnlocked()) {
							if (!blstatus.equals("ROOTABLE")) {
								MyLogger.getLogger().info("Your phone bootloader cannot be officially unlocked");
								MyLogger.getLogger().info("You can now unplug and restart your phone");
							}
							else {
								MyLogger.getLogger().info("Now unplug your device and restart it into fastbootmode");
								String result = (String)WidgetTask.openWaitDeviceForFastboot(shlSonyericsson);
								if (result.equals("OK")) {
									WidgetTask.openBLWizard(shlSonyericsson, serial, imei, ulcode, null, "U");
								}
								else {
									MyLogger.getLogger().info("Bootloader unlock canceled");
								}
							}
						}
						else {
							WidgetTask.openBLWizard(shlSonyericsson, serial, imei, ulcode, flash, j.isRelocked()?"U":"R");
							flash.closeDevice();
							MyLogger.initProgress(0);
							MyLogger.getLogger().info("You can now unplug and restart your device");
							DeviceChangedListener.pause(false);								
						}
					}
				});
				ulj.schedule();
			}
			catch (Exception e) {
				flash.closeDevice();
				DeviceChangedListener.pause(false);
				MyLogger.getLogger().info("Bootloader unlock canceled");
				MyLogger.initProgress(0);
			}
		}
		else {
			MyLogger.getLogger().info("Bootloader unlock canceled");
		}
		}
		catch (Exception e) {
			MyLogger.getLogger().error(e.getMessage());
			e.printStackTrace();
		}
	}

	public void doExportDevice(String device) throws Exception {
		File ftd = new File(OS.getWorkDir()+OS.getFileSeparator()+"devices"+OS.getFileSeparator()+device+".ftd");
		byte buffer[] = new byte[10240];
	    FileOutputStream stream = new FileOutputStream(ftd);
	    JarOutputStream out = new JarOutputStream(stream);
	    out.setLevel(Deflater.BEST_SPEED);
	    File root = new File(OS.getWorkDir()+OS.getFileSeparator()+"devices"+OS.getFileSeparator()+device);
	    int rootindex = root.getAbsolutePath().length();
		Collection<File> c = OS.listFileTree(root);
		Iterator<File> i = c.iterator();
		while (i.hasNext()) {
			File entry = i.next();
			String name = entry.getAbsolutePath().substring(rootindex-device.length());
			if (entry.isDirectory()) name = name+"/";
		    JarEntry jarAdd = new JarEntry(name);
	        out.putNextEntry(jarAdd);
	        if (!entry.isDirectory()) {
	        InputStream in = new FileInputStream(entry);
	        while (true) {
	          int nRead = in.read(buffer, 0, buffer.length);
	          if (nRead <= 0)
	            break;
	          out.write(buffer, 0, nRead);
	        }
	        in.close();
	        }
		}
		out.close();
	    stream.close();
	}

	public void doBackupTA() {
		Bundle bundle = new Bundle();
		bundle.setSimulate(GlobalConfig.getProperty("simulate").toLowerCase().equals("yes"));
		final X10flash flash = new X10flash(bundle,shlSonyericsson);
		try {
			MyLogger.getLogger().info("Please connect your device into flashmode.");
			String result = (String)WidgetTask.openWaitDeviceForFlashmode(shlSonyericsson,flash);
			if (result.equals("OK")) {
				BackupTAJob fjob = new BackupTAJob("Flash");
				fjob.setFlash(flash);
				fjob.schedule();
			}
			else
				MyLogger.getLogger().info("Flash canceled");
		}
		catch (Exception e){
			MyLogger.getLogger().error(e.getMessage());
			MyLogger.getLogger().info("Flash canceled");
			if (flash.getBundle()!=null)
				flash.getBundle().close();
		}
	}

	public void doRoot() {
		String pck = WidgetTask.openRootPackageSelector(shlSonyericsson);
		RootJob rj = new RootJob("Root device");
		rj.setRootPackage(pck);
		rj.setParentShell(shlSonyericsson);
		if (Devices.getCurrent().getVersion().contains("2.3")) {
			rj.setAction("doRootzergRush");
		}
		else
			if (!Devices.getCurrent().getVersion().contains("4.0") && !Devices.getCurrent().getVersion().contains("4.1"))
				rj.setAction("doRootpsneuter");
			else {
				if (Devices.getCurrent().getVersion().contains("4.0.3"))
					rj.setAction("doRootEmulator");
				else
					if (Devices.getCurrent().getVersion().contains("4.0"))
						rj.setAction("doRootAdbRestore");
					else {
						if (Devices.getCurrent().getVersion().contains("4.1")) {
							rj.setAction("doRootServiceMenu");							
						}
						else {
							MessageBox mb = new MessageBox(shlSonyericsson,SWT.ICON_ERROR|SWT.OK);
							mb.setText("Errorr");
							mb.setMessage("No root exploit for your device");
							int result = mb.open();
						}
					}
			}
		rj.schedule();
	}
	
	public void doRoot(String rootmethod) {
		String pck = WidgetTask.openRootPackageSelector(shlSonyericsson);
		RootJob rj = new RootJob("Root device");
		rj.setRootPackage(pck);
		rj.setParentShell(shlSonyericsson);
		rj.setAction(rootmethod);							
		rj.schedule();		
	}
}