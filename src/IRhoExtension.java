

import android.graphics.Rect;

public interface IRhoExtension {

    interface IAlertResult {
        void setPending();
        void confirm();
        void cancel();
    }
    interface IPromptResult {
        void setPending();
        void confirm(String res);
        void cancel();
    }

    public enum LoadErrorReason { STOP, INTERNAL_ERROR, BAD_LICENSE }

    IRhoWebView onCreateWebView(IRhoExtManager extManager, int tabIndex);
    boolean onWebViewCreated(IRhoExtManager extManager, IRhoWebView ext, boolean res);
    boolean onSetPropertiesData(IRhoExtManager extManager, String propId, String data, int pos, int total, IRhoWebView ext, boolean res);
    boolean onSetPropertiesDataEnd(IRhoExtManager extManager, IRhoWebView ext, boolean res);
    boolean onSetProperty(IRhoExtManager extManager, String name, String value, IRhoWebView ext, boolean res);
    boolean onBeforeNavigate(IRhoExtManager extManager, String url, IRhoWebView ext, boolean res);
    boolean onNavigateStarted(IRhoExtManager extManager, String url, IRhoWebView ext, boolean res);
    boolean onNavigateProgress(IRhoExtManager extManager, String url, int pos, int total, IRhoWebView ext, boolean res);
    boolean onNavigateComplete(IRhoExtManager extManager, String url, IRhoWebView ext, boolean res);
    boolean onDocumentComplete(IRhoExtManager extManager, String url, IRhoWebView ext, boolean res);
    boolean onAlert(IRhoExtManager extManager, String message, IRhoWebView ext, IAlertResult alertResult, boolean res);
    boolean onConfirm(IRhoExtManager extManager, String message, IRhoWebView ext, IAlertResult confirmResult, boolean res);
    boolean onPrompt(IRhoExtManager extManager, String message, String defaultResponse, IRhoWebView ext, IPromptResult promptResult, boolean res);
    boolean onSelect(IRhoExtManager extManager, String[] items, int selected, IRhoWebView ext, boolean res);
    boolean onStatus(IRhoExtManager extManager, String status, IRhoWebView ext, boolean res);
    boolean onTitle(IRhoExtManager extManager, String title, IRhoWebView ext, boolean res);
    boolean onConsole(IRhoExtManager extManager, String message, IRhoWebView ext, boolean res);
    boolean onInputMethod(IRhoExtManager extManager, boolean enabled, String type, Rect area, IRhoWebView ext, boolean res);
    boolean onNavigateError(IRhoExtManager extManager, String url, LoadErrorReason reason, IRhoWebView ext, boolean res);
    boolean onAuthRequired(IRhoExtManager extManager, String type, String url, String realm, IRhoWebView ext, boolean res); 

    void onAppActivate(IRhoExtManager extManager, boolean bActivate);

    //EkiohLocation getCachedLocation(IRhoExtManager extManager, IRhoExtData ext);
    boolean startLocationUpdates(IRhoExtManager extManager, boolean highAccuracy, IRhoWebView ext, boolean res); 
    boolean stopLocationUpdates(IRhoExtManager extManager, IRhoWebView ext, boolean res);

    boolean onNewConfig(IRhoExtManager extManager, IRhoConfig config, String name, boolean res);

    String onGetProperty(IRhoExtManager extManager, String name);
}

