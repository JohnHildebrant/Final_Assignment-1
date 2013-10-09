

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnKeyListener;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsoluteLayout;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.rhomobile.rhodes.Capabilities;
import com.rhomobile.rhodes.Logger;
import com.rhomobile.rhodes.RhodesActivity;
import com.rhomobile.rhodes.RhodesApplication;
import com.rhomobile.rhodes.RhodesService;
import com.rhomobile.rhodes.WebView;
import com.rhomobile.rhodes.extmanager.IRhoExtension.IAlertResult;
import com.rhomobile.rhodes.extmanager.IRhoExtension.IPromptResult;
import com.rhomobile.rhodes.mainview.MainView;
import com.rhomobile.rhodes.util.ContextFactory;
import com.rhomobile.rhodes.util.Utils;
import com.rhomobile.rhodes.webview.GoogleWebView;

public class RhoExtManagerImpl implements IRhoExtManager {
    private static final String TAG = RhoExtManagerImpl.class.getSimpleName();

    private LinkedHashMap <String, IRhoExtension> mExtensions = new LinkedHashMap<String, IRhoExtension>();
    private Map<String, IRhoConfig> mConfigs = new LinkedHashMap<String, IRhoConfig>();
    private ArrayList<IRhoListener> mListeners = new ArrayList<IRhoListener>();
    private ArrayList<IRhoListener> mKeyListeners = new ArrayList<IRhoListener>();
    private boolean mLogError = false;
    private boolean mLogWarning = false;
    private boolean mLogInfo = false;
    private boolean mLogUser = false;
    private boolean mLogDebug = false;
    private boolean mFirstNavigate = true;
    private IRhoWebViewConfig mWebViewConfig = null;

    private IRhoWebView makeDefExtData(View view) {
        return RhodesActivity.safeGetInstance().getMainView().getWebView(view);
    }
    
    private static native void nativeRequireRubyFile(String path);
    static native String nativeJSCallEntryPoint(String query);

    static int getResId(String className, String idName) {
        className = ContextFactory.getAppContext().getPackageName() + ".R$" + className;
        try {
            Class<?> rClass = Class.forName(className);
            Field field = rClass.getDeclaredField(idName);
            return field.getInt(null);
        } catch (Throwable e) {
            throw new IllegalArgumentException("Cannot get " + className + "." + idName, e);
        }
    }

	@Override
	public IRhoExtension getExtByName(String strName) {
	    synchronized (mExtensions) {
	        return mExtensions.get(strName);
	    }
	}

    public void registerExtension(String strName, IRhoExtension ext) {
        Logger.T(TAG, "Registering extension: " + strName);
        synchronized (mExtensions) {
            mExtensions.put(strName, ext);
        }
    }

    @Override
    public void addRhoListener(IRhoListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }
    

    @Override
    public void setConfig(String name, IRhoConfig config) {
        Logger.T(TAG, "Adding new config: " + name);
        synchronized(mExtensions) {
            boolean res = false;
            for(IRhoExtension ext: mExtensions.values()) {
                ext.onNewConfig(this, config, name, res);
            }
        }
        synchronized(mConfigs) {
            mConfigs.put(name, config);
        }
    }

    @Override
    public IRhoConfig getConfig(String name) {
        synchronized(mConfigs) {
            return mConfigs.get(name);
        }
    }

    @Override
    public Context getContext() {
        return ContextFactory.getUiContext();
    }

    @Override
	public IRhoWebView getWebView() {
        MainView mainView = RhodesActivity.safeGetInstance().getMainView();
        return mainView != null ? mainView.getWebView(mainView.activeTab()) : null;
    }

    @Override
    public View getTopView() {
        MainView mainView = RhodesActivity.safeGetInstance().getMainView();
        return mainView != null ? mainView.getView() : null;
    }

    @Override
    public void onUnhandledProperty(String extName, String name, String value, IRhoWebView extData) {
        IRhoExtension ext = null;
        synchronized (mExtensions) {
            ext = mExtensions.get(extName);
        }
        if (ext != null) {
            ext.onSetProperty(this, name, value, extData, false);
        }
    }

    @Override
    public void executeRubyCallback(String url, String body, String data, boolean waitForResponse) {
        RhodesService.doRequestEx(url, body, data, waitForResponse);
    }

    @Override
    public void executeRubyCallbackWithJsonBody(String url, String json, String data, boolean waitForResponse) {
        RhodesService.doRequestJson(url, json, data, waitForResponse);
    }

    @Override
    public void requireRubyFile(String filePath) {
        Logger.I(TAG, "Require ruby file: " + filePath);
        nativeRequireRubyFile(filePath);
    }

    @Override
    public void navigate(String url) {
        WebView.navigate(url, WebView.activeTab());
    }

    @Override
    public void executeJavascript(String jsFunction) {
        WebView.executeJs(jsFunction, WebView.activeTab());
    }

    @Override
    public String getCurrentUrl() {
        MainView mainView = RhodesActivity.safeGetInstance().getMainView();
        return mainView != null ? mainView.currentLocation(mainView.activeTab()) : "";
    }

    @Override
    public void stopNavigate(final IRhoExtension.LoadErrorReason reason) {
        
        //TODO: cannot do stopNavigate due to EkiohView bug
        //WebView.stopNavigate();
        
        final RhodesActivity activity = RhodesActivity.safeGetInstance();
        final MainView mainView = activity.getMainView();
        
        // In case stopNavigate is called from onBeforeNavigate
        // we must be aware that all other parties (extensions) finish to proceed current event
        activity.post(new Runnable() {
            @Override public void run() {
                onLoadError(mainView.getWebView(mainView.activeTab()).getView(), reason);
            }
        });
    }

    @Override
    public void historyForward() {
        WebView.navigateForward();
    }

    @Override
    public void historyBack() {
        WebView.navigateBack();
    }
    
    @Override
    public boolean onKey(int keyCode, KeyEvent event){
    	for(IRhoListener listener: mKeyListeners){
    		if(listener.onKey(keyCode, event)){
    			return true;
    		}
    	}
    	return false;
    }

    @Override
    public void refreshPage(boolean bFromCache) {
        WebView.refresh(WebView.activeTab());
    }

    @Override
    public void quitApp() {
        RhodesService.exit();
    }

    @Override
    public void setFullScreen(boolean fullScreen) {
        
    }

    @Override
    public void minimizeApp() {
        // TODO Auto-generated method stub
    }

    @Override
    public void restoreApp() {
        // TODO Auto-generated method stub
    }

    @Override
    public void zoomPage(float fZoom) {
        WebView.setZoom((int)(fZoom * 100));
    }

    @Override
    public void zoomText(int nZoom) {
	switch (nZoom)
	{
	case 0:
	    WebView.setTextZoom(IRhoWebView.TextZoom.SMALLEST);
	    break;
	case 1:
	    WebView.setTextZoom(IRhoWebView.TextZoom.SMALLER);
	    break;
	case 2:
	    WebView.setTextZoom(IRhoWebView.TextZoom.NORMAL);
	    break;
	case 3:
	    WebView.setTextZoom(IRhoWebView.TextZoom.LARGER);
	    break;
	case 4:
	    WebView.setTextZoom(IRhoWebView.TextZoom.LARGEST);
	    break;
	default:
	    WebView.setTextZoom(IRhoWebView.TextZoom.NORMAL);
	    break;
	}
    }

    @Override
    public String getBuildConfigItem(String name) {
        return RhodesService.getBuildConfig(name);
    }

    /** 
     * @return is extension allowed to navigate to its start page
     */
    @Override
    public boolean onStartNewConfig() {
        return Capabilities.SHARED_RUNTIME_ENABLED;
    }

    private String mLicenseToken;
    private String mLicenseCompany;
    private String mAppName;
    private LicenseStatus mLicenseStatus = LicenseStatus.LICENSE_MISSED;

    public void setLicenseCredentials(String token, String company, String appName) {
        Logger.T(TAG, "New license credentials");
        Logger.D(TAG, "License token: " + token);
        Logger.D(TAG, "License company: " + company);
        Logger.D(TAG, "App name: " + appName);

        mLicenseToken = token;
        mLicenseCompany = company;
        mAppName = appName;
        
        mLicenseStatus = checkLicence();
    }

    public LicenseStatus getLicenseStatus() {
        return mLicenseStatus;
    }

    private LicenseStatus checkLicence() {
        if (mLicenseToken == null || mLicenseToken.length() == 0) {
            return LicenseStatus.LICENSE_MISSED;
        }
        if (RhodesService.isMotorolaLicencePassed(mLicenseToken, mLicenseCompany, mAppName)) {
            return LicenseStatus.LICENSE_PASSED;
        }
        else {
            return LicenseStatus.LICENSE_FAILED;
        }
    }

    //-----------------------------------------------------------------------------------------------------------------
    // Rhodes implementation related methods are below

    public IRhoWebView createWebView(RhodesActivity activity, int tabIndex) {
        IRhoWebView res = null;
        synchronized (mExtensions) {
            for (IRhoExtension ext : mExtensions.values()) {
                IRhoWebView view = ext.onCreateWebView(this, tabIndex);
                if (view != null) {
                    view.setConfig(mWebViewConfig);
                    if (res != null) {
                        Logger.W(TAG, "WebView has already created by another extension, overlapping it");
                    }
                    res = view;
                }
            }
            if (res == null) {
                Logger.T(TAG, "Creating Google web view");
                final GoogleWebView googleWebView = new GoogleWebView(activity);
                googleWebView.setConfig(mWebViewConfig);
                res = googleWebView;
                RhodesApplication.runWhen(RhodesApplication.AppState.AppStarted, new RhodesApplication.StateHandler(true) {
                    @Override
                    public void run()
                    {
                        googleWebView.applyWebSettings();
                    }
                });
            }
            AbsoluteLayout containerView = new AbsoluteLayout(activity);
            containerView.addView(res.getView(), new AbsoluteLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 0, 0));
            res.setContainerView(containerView);
            res.setWebClient();
            boolean handled = false;
            //res.addJSInterface(new RhoJSApi(), "__rhoNativeApi");
            for (IRhoExtension ext : mExtensions.values()) {
                handled = ext.onWebViewCreated(this, res, handled);
            }
        }
        return res;
    }
    
    public void enableLogLevelError(boolean enabled) {
        Logger.I(TAG, "RE Error log: " + enabled);
        mLogError = enabled;
    }
    public void enableLogLevelWarning(boolean enabled) { 
        Logger.I(TAG, "RE Warning log: " + enabled);
        mLogWarning = enabled; 
    }
    public void enableLogLevelInfo(boolean enabled) { 
        Logger.I(TAG, "RE Info log: " + enabled);
        mLogInfo = enabled;
    }
    public void enableLogLevelUser(boolean enabled) { 
        Logger.I(TAG, "RE User log: " + enabled);
        mLogUser = enabled;
    }
    public void enableLogLevelDebug(boolean enabled) { 
        Logger.I(TAG, "RE Debug log: " + enabled);
        mLogDebug = enabled;
    }

    void logT(String tag, String msg) {
        if (mLogDebug)
            Logger.I(tag, msg);
    }

    void logI(String tag, String msg) {
        if (mLogInfo)
            Logger.I(tag, msg);
    }

    void logU(String tag, String msg) {
        if (mLogUser)
            Logger.I(tag, msg);
    }

    void logW(String tag, String msg) {
        if (mLogWarning)
            Logger.W(tag, msg);
    }

    void logE(String tag, String msg) {
        if (mLogError)
            Logger.E(tag, msg);
    }

    
    public void onSetPropertiesData(View view,String propId, String data, int position, int total) {
        IRhoWebView rhoWebView = makeDefExtData(view);
        synchronized (mExtensions) {
            boolean res = false;
            for (IRhoExtension ext : mExtensions.values()) {
                res = ext.onSetPropertiesData(this, propId, data, position, total, rhoWebView, res);
            }
        }
    }

    private boolean isFirstNavigate() { 
            return mFirstNavigate;
    }

    private void firstNavigate() {
            mFirstNavigate = false;
    }


    public void onBeforeNavigate(View view, String url) {
        IRhoWebView rhoWebView = makeDefExtData(view);
        if (isFirstNavigate()) {
            Logger.T(TAG, "onBeforeNavigate first time");
            firstNavigate();
        }

        synchronized (mExtensions) {
            boolean res = false;
            for (Map.Entry<String, IRhoExtension> extEntry : mExtensions.entrySet()) {
                Logger.T(TAG, "onBeforeNavigate: " + extEntry.getKey());
                res = extEntry.getValue().onBeforeNavigate(this, url, rhoWebView, res);
            }
        }
    }

    public void onNavigateStarted(View view, String url) {
        IRhoWebView rhoWebView = makeDefExtData(view);

        synchronized (mExtensions) {
            boolean res = false;
            for (Map.Entry<String, IRhoExtension> extEntry : mExtensions.entrySet()) {
                Logger.T(TAG, "onNavigateStarted: " + extEntry.getKey());
                res = extEntry.getValue().onNavigateStarted(this, url, rhoWebView, res);
            }
        }
    }

    public void onNavigateComplete(View view, String url) {
        int tabIndex = RhodesActivity.safeGetInstance().getMainView().getWebViewTab(view);
        IRhoWebView rhoWebView = RhodesActivity.safeGetInstance().getMainView().getWebView(tabIndex);
        synchronized (mExtensions) {
            boolean res = false;
            for (IRhoExtension ext : mExtensions.values()) {
                res = ext.onNavigateComplete(this, url, rhoWebView, res);
            }
        }
    }

    public void onAppActivate(boolean isActivate) {
        Logger.T(TAG, "onAppActivate: " + isActivate);
        synchronized (mExtensions) {
            for (IRhoExtension ext : mExtensions.values()) {
                Logger.T(TAG, "onAppActivate: " + ext.getClass().getSimpleName());
                ext.onAppActivate(this, isActivate);
            }
        }
    }

    public void onAlert(View view, String msg, final IAlertResult alertResult) {
        Logger.T(TAG, "onAlert");
        IRhoWebView rhoWebView = makeDefExtData(view);
        boolean res = false;
        synchronized (mExtensions) {
            for (IRhoExtension ext : mExtensions.values()) {
                res = ext.onAlert(this, msg, rhoWebView, alertResult, res);
            }
        }
        if (!res) {
            alertResult.setPending();
            AlertDialog.Builder builder = new AlertDialog.Builder(ContextFactory.getUiContext());
            builder.setMessage(msg);
            builder.setCancelable(false);
            builder.setTitle(RhodesActivity.safeGetInstance().getTitle());
            builder.setPositiveButton("OK", new OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    alertResult.confirm();
                    dialog.dismiss();
                }});
            builder.setOnKeyListener(new OnKeyListener() {
                @Override public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_SEARCH) {
                        alertResult.cancel();
                        dialog.dismiss();
                        return true;
                    }
                    return false;
                }});
            builder.create().show();
        }
    }

    public void onConfirm(View view, String msg, final IAlertResult confirmResult) {
        IRhoWebView rhoWebView = makeDefExtData(view);
        boolean res = false;
        synchronized (mExtensions) {
            for (IRhoExtension ext : mExtensions.values()) {
                res = ext.onConfirm(this, msg, rhoWebView, confirmResult, res);
            }
        }
        if (!res) {
            confirmResult.setPending();
            AlertDialog.Builder builder = new AlertDialog.Builder(ContextFactory.getUiContext());
            builder.setMessage(msg);
            builder.setCancelable(true);
            builder.setTitle(RhodesActivity.safeGetInstance().getTitle());
            builder.setPositiveButton("OK", new OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    confirmResult.confirm();
                    dialog.dismiss();
                }});
            builder.setNegativeButton("Cancel", new OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    confirmResult.cancel();
                    dialog.dismiss();
                }});
            builder.setOnKeyListener(new OnKeyListener() {
                @Override public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_SEARCH) {
                        confirmResult.cancel();
                        dialog.dismiss();
                        return true;
                    }
                    return false;
                }});
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    confirmResult.cancel();
                    dialog.dismiss();
                }
            });
            builder.create().show();
        }
    }

    public void onConsole(View view, String msg) {
        IRhoWebView rhoWebView = makeDefExtData(view);
        boolean res = false;
        synchronized (mExtensions) {
            for (IRhoExtension ext : mExtensions.values()) {
                res = ext.onConsole(this, msg, rhoWebView, res);
            }
        }
    }

    public void onInputMethod(View view, boolean enabled, String type, Rect area) {
        IRhoWebView rhoWebView = makeDefExtData(view);
        boolean res = false;
        synchronized (mExtensions) {
            for (IRhoExtension ext : mExtensions.values()) {
                res = ext.onInputMethod(this, enabled, type, area, rhoWebView, res);
            }
        }
    }

    public void onLoadEnd(View view, String url, long arg2, long arg3) {
        int tabIndex = RhodesActivity.safeGetInstance().getMainView().getWebViewTab(view);
        IRhoWebView rhoWebView = RhodesActivity.safeGetInstance().getMainView().getWebView(tabIndex);
        boolean res = false;
        synchronized (mExtensions) {
            for (IRhoExtension ext : mExtensions.values()) {
                res = ext.onNavigateComplete(this, url, rhoWebView, res);
            }
        }
    }

    public void onLoadError(View view, IRhoExtension.LoadErrorReason reason) {
        IRhoWebView rhoWebView = makeDefExtData(view);
        boolean res = false;
        synchronized (mExtensions) {
            for (IRhoExtension ext : mExtensions.values()) {
                res = ext.onNavigateError(this, "", reason, rhoWebView, res);
            }
        }
    }

    public void onLoadProgress(View view, int val, int total) {
        IRhoWebView rhoWebView = makeDefExtData(view);
        boolean res = false;
        synchronized (mExtensions) {
            for (IRhoExtension ext : mExtensions.values()) {
                res = ext.onNavigateProgress(this, "", val, total, rhoWebView, res);
            }
        }
    }

    public void onMetaEnd(View view) {
        IRhoWebView rhoWebView = makeDefExtData(view);
        boolean res = false;
        synchronized (mExtensions) {
            for (IRhoExtension ext : mExtensions.values()) {
                res = ext.onSetPropertiesDataEnd(this, rhoWebView, res);
            }
        }
    }

    public void onPrompt(View view, String prompt, String defaultResponse, final IPromptResult promptResult) {
        if (defaultResponse.startsWith("__rhoNativeApiCall")) {
            Logger.D(TAG, "Execute JS hook: " + prompt);
            String res = nativeJSCallEntryPoint(prompt);
            Logger.D(TAG, "JS result: " + res);
            promptResult.setPending();
            promptResult.confirm(res);
            return;
        }
        IRhoWebView rhoWebView = makeDefExtData(view);
        boolean res = false;
        synchronized (mExtensions) {
            for (IRhoExtension ext : mExtensions.values()) {
                res = ext.onPrompt(this, prompt, defaultResponse, rhoWebView, promptResult, res);
            }
        }
        if (!res) {
            promptResult.setPending();
            final LinearLayout layout = new LinearLayout(ContextFactory.getUiContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(10, 10, 10, 10);
            final TextView promptView = new TextView(ContextFactory.getUiContext(), null, android.R.style.Widget_TextView);
            promptView.setPadding(10, 10, 10, 10);
            final EditText input = new EditText(ContextFactory.getUiContext());
            
            layout.addView(promptView, 0, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            layout.addView(input, 1, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            AlertDialog.Builder builder = new AlertDialog.Builder(ContextFactory.getUiContext());
            builder.setView(layout);

            promptView.setText(prompt);
            input.setText(defaultResponse);

            builder.setCancelable(true);
            builder.setTitle(RhodesActivity.safeGetInstance().getTitle());
            builder.setPositiveButton("OK", new OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    promptResult.confirm(input.getText().toString());
                    dialog.dismiss();
                }});
            builder.setNegativeButton("Cancel", new OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    promptResult.cancel();
                    dialog.dismiss();
                }});
            builder.setOnKeyListener(new OnKeyListener() {
                @Override public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_SEARCH) {
                        promptResult.cancel();
                        dialog.dismiss();
                        return true;
                    }
                    return false;
                }});
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    promptResult.cancel();
                    dialog.dismiss();
                }
            });
            builder.create().show();
        }
    }

    public void onSelect(View view, String[] lines, int pos) {
        IRhoWebView rhoWebView = makeDefExtData(view);
        boolean res = false;
        synchronized (mExtensions) {
            for (IRhoExtension ext : mExtensions.values()) {
                res = ext.onSelect(this, lines, pos, rhoWebView, res);
            }
        }
    }

    public void onStatus(View view, String msg) {
        IRhoWebView rhoWebView = makeDefExtData(view);
        boolean res = false;
        synchronized (mExtensions) {
            for (IRhoExtension ext : mExtensions.values()) {
                res = ext.onStatus(this, msg, rhoWebView, res);
            }
        }
    }

    public void onTitle(View view, String title) {
        RhodesActivity.safeGetInstance().setTitle(title);
        IRhoWebView rhoWebView = makeDefExtData(view);
        boolean res = false;
        synchronized (mExtensions) {
            for (IRhoExtension ext : mExtensions.values()) {
                res = ext.onStatus(this, title, rhoWebView, res);
            }
        }
    }
    
    public void onAuthRequired(View view, String type, String url, String realm) {
        IRhoWebView rhoWebView = makeDefExtData(view);
        boolean res = false;
        synchronized (mExtensions) {
            for (IRhoExtension ext : mExtensions.values()) {
                res = ext.onAuthRequired(this, type, url, realm, rhoWebView, res);
            }
        }
    }

    public void startLocationUpdates(View view, boolean val) {
        IRhoWebView rhoWebView = makeDefExtData(view);
        boolean res = false;
        synchronized (mExtensions) {
            for (IRhoExtension ext : mExtensions.values()) {
                res = ext.startLocationUpdates(this, val, rhoWebView, res);
            }
        }
    }

    public void stopLocationUpdates(View view) {
        IRhoWebView rhoWebView = makeDefExtData(view);
        boolean res = false;
        synchronized (mExtensions) {
            for (IRhoExtension ext : mExtensions.values()) {
                res = ext.stopLocationUpdates(this, rhoWebView, res);
            }
        }
    }

    @Override
    public void startKeyEventUpdates(IRhoListener listener)
    {
    	startKeyEventUpdates(listener, false);
    }
    
    @Override
    public void startKeyEventUpdates(IRhoListener listener, boolean makeImportant)
    {
    	Logger.D(TAG, "Registered KeyEvent Listener");
		if(makeImportant){
			mKeyListeners.remove(listener);
			mKeyListeners.add(0, listener);
		}
		else{
			if(!mKeyListeners.contains(listener)){
				mKeyListeners.add(listener);
			}
		}
    }
    
    @Override
    public void stopKeyEventUpdates(IRhoListener listener)
    {
    	Logger.D(TAG, "Unregistered KeyEvent Listener");
		mKeyListeners.remove(listener);
    }

    public String getProperty(String name) {
        synchronized (mExtensions) {
            for (Map.Entry<String, IRhoExtension> entry : mExtensions.entrySet()) {
                String res = entry.getValue().onGetProperty(this, name);
                if (res != null) {
                    Logger.T(TAG, "Property from '" + entry.getKey() + "' extension - " + name + ": " + res);
                    return res;
                }
            }
        }
        return null;
    }
    
    public void createRhoListeners() {
        for (String classname: RhodesStartupListeners.ourRunnableList) {
            if (classname.length() == 0) continue;
            
            Class<? extends IRhoListener> klass = null;
            try {
                klass = Class.forName(classname).asSubclass(IRhoListener.class);
            } catch (ClassNotFoundException e) {
                Utils.platformLog("RhodesActivity", "processStartupListeners() : ClassNotFoundException for ["+classname+"]");
                e.printStackTrace();
            }
            IRhoListener listener = null;
            try {
                if (klass != null) {
                    listener = klass.newInstance();
                }
            } catch (InstantiationException e) {
                Utils.platformLog("RhodesActivity", "processStartupListeners() : InstantiationException for ["+classname+"]");
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                Utils.platformLog("RhodesActivity", "processStartupListeners() : IllegalAccessException for ["+classname+"]");
                e.printStackTrace();
            }
            if (listener != null) {
                listener.onCreateApplication(this);
            }
        }
    }

    public void onCreateActivity(RhodesActivity activity, Intent intent) {
        for (IRhoListener listener: mListeners) {
            listener.onCreate(activity, intent);
        }
        activity.getMainView().setWebView(createWebView(activity, 0), 0);
    }
    public void onStartActivity(RhodesActivity activity) {
        for (IRhoListener listener: mListeners) {
            listener.onStart(activity);
        }
    }
    public void onResumeActivity(RhodesActivity activity) {
        for (IRhoListener listener: mListeners) {
            listener.onResume(activity);
        }
    }
    public void onPauseActivity(RhodesActivity activity) {
        for (IRhoListener listener: mListeners) {
            listener.onPause(activity);
        }
    }
    public void onStopActivity(RhodesActivity activity) {
        for (IRhoListener listener: mListeners) {
            listener.onStop(activity);
        }
    }
    public void onDestroyActivity(RhodesActivity activity) {
        for (IRhoListener listener: mListeners) {
            listener.onDestroy(activity);
        }
    }
    public void onNewIntent(RhodesActivity activity, Intent intent) {
        for (IRhoListener listener: mListeners) {
            listener.onNewIntent(activity, intent);
        }
    }
    public void onActivityResult(RhodesActivity activity, int reqCode, int resCode, Intent intent) {
        for (IRhoListener listener: mListeners) {
            listener.onActivityResult(activity, reqCode, resCode, intent);
        }
    }
    public Dialog onCreateDialog(RhodesActivity activity, int id/*, Bundle args*/) {
        Dialog res = null;
        for (IRhoListener listener: mListeners) {
            res = listener.onCreateDialog(activity, id);
            if (res != null)
                break;
        }
        return res;
    }
    public void onConfigurationChanged(RhodesActivity activity, Configuration newConfig) {
        for (IRhoListener listener: mListeners) {
            listener.onConfigurationChanged(activity, newConfig);
        }
    }

    @Override
    public void setWebViewConfig(IRhoWebViewConfig config) {
        Logger.T(TAG, "Set WebView config");

        mWebViewConfig = config;
        MainView mainView = RhodesActivity.safeGetInstance().getMainView();
        for (int i = 0; i < mainView.getTabsCount(); ++i) {
            Logger.T(TAG, "Set WebView config: tab " + i);
            mainView.getWebView(i).setConfig(config);
        }
    }

}

