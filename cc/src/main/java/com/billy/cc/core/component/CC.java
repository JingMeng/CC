package com.billy.cc.core.component;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import android.text.TextUtils;
import android.util.Log;

import com.billy.android.pools.ObjPool;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 缁勪欢璋冪敤
 * CC = Component Caller
 * @author billy.qi
 */
@SuppressLint("PrivateApi")

public class CC {
    private static final String TAG = "ComponentCaller";
    private static final String VERBOSE_TAG = "ComponentCaller_VERBOSE";
    /**
     * 榛樿瓒呮椂鏃堕棿涓?绉?     */
    private static final long DEFAULT_TIMEOUT = 2000;
    static boolean DEBUG = false;
    static boolean VERBOSE_LOG = false;
    /**
     * 鏄惁鍝嶅簲璺╝pp鐨勭粍浠惰皟鐢?     * 涓轰簡鏂逛究寮€鍙戣皟璇曪紝榛樿璁剧疆涓哄厑璁稿搷搴旇法app缁勪欢璋冪敤
     * 涓轰簡瀹夊叏锛宎pp涓婄嚎鏃跺彲浠ュ皢姝ゅ€艰缃负false锛岄伩鍏嶈鎭舵剰璋冪敤
     */
    static boolean RESPONSE_FOR_REMOTE_CC = false;
    /**
     * 濡傛灉璋冪敤鍒板綋鍓峚pp鍐呮病鏈夌殑缁勪欢锛屾槸鍚﹀皾璇曞幓鍏跺畠app鍐呰皟鐢紙榛樿涓篺alse锛?     */
    static boolean CALL_REMOTE_CC_IF_NEED = false;

    private volatile CCResult result;

    private final byte[] wait4resultLock = new byte[0];

    private static Application application;

    WeakReference<Activity> cancelOnDestroyActivity;

    WeakReference<Fragment> cancelOnDestroyFragment;

    private volatile boolean waiting;

    static {
        Application app = CCUtil.initApplication();
        if (app != null) {
            init(app);
        }
    }

    /**
     * 鍒濆鍖栨柟娉?     * 浠呭垵濮嬪寲CC锛屼笉鍒濆鍖栫粍浠跺拰鎷︽埅鍣?     * 鍦ˋpplication.onCreate(...)涓皟鐢?     * @param app Application
     */
    public static synchronized void init(Application app) {
        init(app, false, false);
    }

    /**
     * 鍒濆鍖栨柟娉?     * 鍚屾椂鍒濆鍖栫粍浠跺拰鍏ㄥ眬鎷︽埅鍣?     * @param app Application
     * @param initComponents 濡傛灉璁剧疆涓簍rue鍒欏悓鏃跺垵濮嬪寲缁勪欢
     * @param initGlobalInterceptors 濡傛灉璁剧疆涓簍rue鍒欏悓鏃跺垵濮嬪寲鍏ㄥ眬鎷︽埅鍣?     */
    public static synchronized void init(Application app, boolean initComponents, boolean initGlobalInterceptors) {
        if (application == null && app != null) {
            application = app;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                application.registerActivityLifecycleCallbacks(new CCMonitor.ActivityMonitor());
            }
        }
        if (initComponents) {
            ComponentManager.init();
        }
        if (initGlobalInterceptors) {
            GlobalCCInterceptorManager.init();
        }
    }
    
    private static final ObjPool<Builder, String> BUILDER_POOL = new ObjPool<Builder, String>() {
        @Override
        protected Builder newInstance(String componentName) {
            return new Builder();
        }
    };

    private WeakReference<Context> context;
    /**
     * 缁勪欢鍚嶇О
     */
    private String componentName;
    /**
     * 缁勪欢涓煇涓姛鑳界殑鍚嶇О锛岀敤浠ュ尯鍒悓涓€涓粍浠朵腑涓嶅悓鍔熻兘鐨勮皟鐢?     */
    private String actionName;
    private final Map<String, Object> params = new HashMap<>();
    /**
     * 鍥炶皟瀵硅薄
     */
    private IComponentCallback callback;
    /**
     * 鏄惁寮傛鎵ц
     */
    private boolean async;
    private final List<ICCInterceptor> interceptors = new ArrayList<>();
    private boolean callbackOnMainThread;
    /**
     * 璋冪敤瓒呮椂鏃堕棿锛岄粯璁ゅ€硷紙鍚屾璋冪敤锛?000锛?寮傛璋冪敤锛?锛?     */
    private long timeout = -1;
    long timeoutAt;
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private String callId;
    private volatile boolean canceled = false;
    private volatile boolean timeoutStatus = false;
    private boolean withoutGlobalInterceptor = false;

    private CC(String componentName) {
        this.componentName = componentName;
    }

    /**
     * 鍒涘缓CC瀵硅薄鐨凚uilder<br>
     * <b>姝ゅ璞′細琚獵C妗嗘灦澶嶇敤锛岃鍕垮湪绋嬪簭涓繚瀛?/b>
     * @param componentName 瑕佽皟鐢ㄧ殑缁勪欢鍚嶇О
     * @return 鍒涘缓CC瀵硅薄鐨凚uilder
     */
    public static Builder obtainBuilder(String componentName) {
        return BUILDER_POOL.get(componentName);
    }

    /**
     * 鑾峰彇褰撳墠app鐨凙pplication瀵硅薄
     * @return application瀵硅薄
     */
    public static Application getApplication() {
        return application;
    }

    /**
     * CC鐨凚uilder,鏀寔閾惧紡璋冪敤<br>
     * 鏈€缁堥€氳繃璋冪敤{@link Builder#build()}鏂规硶鑾峰彇CC瀵硅薄
     * <b>姝ゅ璞′細琚獵C妗嗘灦澶嶇敤锛岃鍕垮湪绋嬪簭涓繚瀛?/b>
     */
    public static class Builder implements ObjPool.Resetable, ObjPool.Initable<String> {
        private CC cr;
        
        private Builder() { }

        /**
         * 璁剧疆context
         * @param context 璁剧疆涓簄ull鏃舵棤鏁?br>
         *                璋冪敤鍚屼竴涓猘pp鐨勭粍浠舵椂锛岀粍浠跺疄鐜版柟閫氳繃cc.getContext()灏嗚繑鍥炴鍊?br>
         *                璋冪敤鍏跺畠app鐨勭粍浠舵椂锛岀粍浠跺疄鐜版柟閫氳繃cc.getContext()灏嗚繑鍥炵粍浠舵墍鍦╝pp鐨凙pplication瀵硅薄
         * @return Builder鑷韩
         */
        public Builder setContext(Context context) {
            if (context != null) {
                cr.context = new WeakReference<>(context);
            }
            return this;
        }

        /**
         * 涓嶉檺鍒惰秴鏃舵椂闂?         * @return Builder鑷韩
         */
        public Builder setNoTimeout() {
            return setTimeout(0);
        }

        /**
         * 璁剧疆瓒呮椂鏃堕棿
         * @param timeout 瓒呮椂鏃堕棿闄愬埗(ms)
         * @return Builder鑷韩
         */
        public Builder setTimeout(long timeout) {
            if (timeout >= 0) {
                cr.timeout = timeout;
            } else {
                logError("Invalid timeout value:" + timeout
                        + ", timeout should >= 0. timeout will be set as default:"
                        + DEFAULT_TIMEOUT);
            }
            return this;
        }

        /**
         * 鐢ㄤ簬璋冨彇鍚屼竴涓粍浠剁殑涓嶅悓action锛堝彲浠ョ悊瑙ｄ负鍒嗙粍鐨勬蹇碉細灏嗕笉鍚岀殑action鍒嗙粍鍦ㄤ竴涓粍浠堕噷瀵瑰鎻愪緵鏈嶅姟锛?         * @param actionName action鐨勫悕绉帮紝缁勪欢鍦ㄦ墽琛屾椂鍙牴鎹鍊兼墽琛屼笉鍚岀殑鍔ㄤ綔锛岃繑鍥炰笉鍚岀殑淇℃伅
         * @return Builder鑷韩
         */
        public Builder setActionName(String actionName) {
            cr.actionName = actionName;
            return this;
        }

        public Builder withoutGlobalInterceptor() {
            cr.withoutGlobalInterceptor = true;
            return this;
        }

        /**
         * 璁剧疆缁勪欢璋冪敤鐨勫弬鏁帮紙灏嗘竻绌轰箣鍓嶈缃殑鍙傛暟鍒楄〃锛?         * @param params 鍙傛暟 {@link Map} 绫诲瀷
         * @return Builder鑷韩
         */
        public Builder setParams(Map<String, Object> params) {
            cr.params.clear();
            return addParams(params);
        }

        /**
         * 鍚戠粍浠惰皟鐢ㄧ殑鍙傛暟鍒楄〃涓坊鍔犲弬鏁?         * @param params 鍙傛暟 {@link Map} 绫诲瀷
         * @return Builder鑷韩
         */
        public Builder addParams(Map<String, Object> params) {
            if (params != null) {
                for (String key : params.keySet()) {
                    addParam(key, params.get(key));
                }
            }
            return this;
        }

        /**
         * 娣诲姞璋冪敤鍙傛暟
         * @param key 鍙傛暟鐨刱ey
         * @param value 鍙傛暟鐨剉alue
         * @return Builder鑷韩
         */
        public Builder addParam(String key, Object value) {
            cr.params.put(key, value);
            return this;
        }
        /**
         * 娣诲姞缁勪欢璋冪敤鍓嶇殑鎷︽埅鍣?         * @param interceptor 鎷︽埅鍣?         * @return Builder鑷韩
         */
        public Builder addInterceptor(ICCInterceptor interceptor) {
            if (interceptor != null) {
                cr.interceptors.add(interceptor);
            }
            return this;
        }
        /**
         * 璁剧疆activity.onDestroy鏃惰嚜鍔╟ancel
         * @param activity 鐩戞帶姝ctivity鐨勭敓鍛藉懆鏈燂紝鍦╫nDestroy鏂规硶琚皟鐢ㄥ悗鑻c鏈墽琛屽畬鍒欒嚜鍔╟ancel
         * @return Builder鑷韩
         */
        public Builder cancelOnDestroyWith(Activity activity) {
            if (activity != null) {
                cr.cancelOnDestroyActivity = new WeakReference<>(activity);
            }
            return this;
        }

        /**
         * 璁剧疆fragment.onDestroy鏃惰嚜鍔╟ancel
         * @param fragment 鐩戞帶姝ragment鐨勭敓鍛藉懆鏈燂紝鍦╫nDestroy鏂规硶琚皟鐢ㄥ悗鑻c鏈墽琛屽畬鍒欒嚜鍔╟ancel
         * @return Builder鑷韩
         */
        public Builder cancelOnDestroyWith(Fragment fragment) {
            if (fragment != null) {
                cr.cancelOnDestroyFragment = new WeakReference<>(fragment);
            }
            return this;
        }



        /**
         * 鏋勫缓CC瀵硅薄
         * @return CC瀵硅薄
         */
        public CC build() {
            CC cr = this.cr;
            //鍥炴敹澶嶇敤builder
            BUILDER_POOL.put(this);
            if (TextUtils.isEmpty(cr.componentName)) {
                logError("ComponentName is empty:" + cr.toString());
            }
            return cr;
        }

        /**
         * 鐢ㄤ簬Builder瀵硅薄姹犲姝ゅ璞＄殑閲嶇疆
         */
        @Override
        public void reset() {
            this.cr = null;
        }

        /**
         * 鐢ㄤ簬Builder瀵硅薄姹犲姝ゅ璞＄殑鍒濆鍖?         * @param componentName 缁勪欢鍚嶇О
         */
        @Override
        public void init(String componentName) {
            this.cr = new CC(componentName);
        }
    }

    @Override
    public String toString() {
        JSONObject json = new JSONObject();
        put(json, "callId", callId);
        put(json, "context", getContext());
        put(json, "componentName", componentName);
        put(json, "actionName", actionName);
        put(json, "timeout", timeout);
        put(json, "withoutGlobalInterceptor", withoutGlobalInterceptor);
        put(json, "callbackOnMainThread", callbackOnMainThread);
        put(json, "params", CCUtil.convertToJson(params));
        put(json, "interceptors", interceptors);
        put(json, "callback", getCallback());
        return json.toString();
    }

    private void put(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public Context getContext() {
        if (context != null) {
            Context context = this.context.get();
            if (context != null) {
                return context;
            }
        }
        return application;
    }


    public String getActionName() {
        return actionName;
    }

    /**
     * get all params
     * @return all params as map
     */
    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * get param(auto class casted) by key
     * @param key key for param
     * @param defaultValue default value if not found or class cast error
     * @param <T> class to cast for param
     * @return class casted param
     */
    public <T> T getParamItem(String key, T defaultValue) {
        T item = getParamItem(key);
        if (item == null) {
            return defaultValue;
        }
        return item;
    }

    /**
     * get param(auto class casted) by key
     * @param key key for param
     * @param <T> class to cast for param
     * @return class casted param
     */
    public <T> T getParamItem(String key) {
        try {
            return (T) params.get(key);
        } catch(Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    boolean isAsync() {
        return async;
    }

    boolean isCallbackOnMainThread() {
        return callbackOnMainThread;
    }

    long getTimeout() {
        return timeout;
    }

    public String getCallId() {
        return callId;
    }

    boolean isCanceled() {
        return canceled;
    }

    /**
     * 鍒ゆ柇鏄惁闇€瑕佷腑姝㈣繍琛岋紝鏈璋冪敤琚墜鍔ㄥ彇娑堟垨宸茶秴鏃躲€?     * 缁勪欢鍦ㄥ鐞嗚€楁椂鎿嶄綔鏃讹紝瑕佹牴鎹鐘舵€佽繘琛屽垽鏂紝浠ュ厤杩涜鏃犳晥鎿嶄綔
     * @return <code>true</code>:闇€瑕佷腑姝㈢户缁墽琛岋紱false:鍙互缁х画杩愯
     */
    public boolean isStopped() {
        return canceled || timeoutStatus;
    }

    boolean isTimeout() {
        return timeoutStatus;
    }

    boolean isWithoutGlobalInterceptor() {
        return withoutGlobalInterceptor;
    }

    CCResult getResult() {
        return result;
    }

    void setResult(CCResult result) {
        finished.set(true);
        this.result = result;
    }

    void setResult4Waiting(CCResult result) {
        try {
            synchronized (wait4resultLock) {
                if (VERBOSE_LOG) {
                    verboseLog(callId, "setResult" + (waiting ? "4Waiting" : "")
                            + ". CCResult:" + result);
                }
                setResult(result);
                if (waiting) {
                    waiting = false;
                    wait4resultLock.notifyAll();
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    void wait4Result() {
        //绛夊緟璋冪敤CC.sendCCResult(callId, result)
        synchronized (wait4resultLock) {
            if (!isFinished()) {
                try {
                    verboseLog(callId, "start waiting for CC.sendCCResult(...)");
                    waiting = true;
                    wait4resultLock.wait();
                    verboseLog(callId, "end waiting for CC.sendCCResult(...)");
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    IComponentCallback getCallback() {
        return callback;
    }

    /**
     * 鍦╫nDestroy鍚庯紝鑷姩cancel
     */
    void cancelOnDestroy(Object reason) {
        if (!isFinished()) {
            if (VERBOSE_LOG) {
                verboseLog(callId, "call cancel on " + reason + " destroyed");
            }
            cancel();
        }
    }

    void addCancelOnFragmentDestroyIfSet() {
        if (cancelOnDestroyFragment == null) {
            return;
        }
        Fragment fragment = cancelOnDestroyFragment.get();
        if (fragment == null) {
            return;
        }
        FragmentManager manager = fragment.getFragmentManager();
        if (manager != null) {
            manager.registerFragmentLifecycleCallbacks(
                    new CCMonitor.FragmentMonitor(this)
                    , false);
        }
    }

    String getComponentName() {
        return componentName;
    }

    List<ICCInterceptor> getInterceptors() {
        return interceptors;
    }

    /**
     * 寮傛璋冪敤锛屼笖涓嶉渶瑕佸洖璋?     * @return callId锛屽彲鐢ㄤ簬鍙栨秷璋冪敤鐨勪换鍔?     */
    public String callAsync() {
        return callAsync(null);
    }
    /**
     * 寮傛璋冪敤,鍦ㄥ紓姝ョ嚎绋嬫墽琛屽洖璋?     * @param callback 鍥炶皟鍑芥暟
     * @return callId 鐢ㄤ簬鍙栨秷
     */
    public String callAsync(IComponentCallback callback) {
        this.callbackOnMainThread = false;
        return processCallAsync(callback);
    }
    /**
     * 寮傛璋冪敤,鍦ㄤ富绾跨▼鎵ц鍥炶皟
     * @param callback 鍥炶皟鍑芥暟
     * @return callId 鐢ㄤ簬鍙栨秷
     */
    public String callAsyncCallbackOnMainThread(IComponentCallback callback) {
        this.callbackOnMainThread = true;
        return processCallAsync(callback);
    }

    private String processCallAsync(IComponentCallback callback) {
        if (callback != null) {
            this.callback = callback;
        }
        this.async = true;
        // An unspecified asynchronous timeout means no timeout.
        if (timeout < 0) {
            timeout = 0;
        }
        setTimeoutAt();
        this.callId = nextCallId();
        this.canceled = false;
        this.timeoutStatus = false;
        if (VERBOSE_LOG) {
            verboseLog(callId, "start to callAsync:" + this);
        }
        ComponentManager.call(this);
        return callId;
    }

    /**
     * 鍚屾璋冪敤
     * @return CCResult
     */
    public CCResult call() {
        this.callback = null;
        this.async = false;
        boolean mainThreadCallWithNoTimeout = timeout == 0 && Looper.getMainLooper() == Looper.myLooper();
        // Synchronous calls on the main thread require a timeout.
        if (mainThreadCallWithNoTimeout || timeout < 0) {
            timeout = DEFAULT_TIMEOUT;
        }
        setTimeoutAt();
        this.callId = nextCallId();
        this.canceled = false;
        this.timeoutStatus = false;
        //鍔犱笂寮€鍏冲垽鏂紝闃叉寮€鍏冲叧闂殑鎯呭喌涓嬩篃鎵цthis.toString()鏂规硶
        if (VERBOSE_LOG) {
            verboseLog(callId, "start to call:" + this);
        }
        return ComponentManager.call(this);
    }

    /**
     * 璁惧畾瓒呮椂
     */
    private void setTimeoutAt() {
        if (timeout > 0) {
            timeoutAt = System.currentTimeMillis() + timeout;
        } else {
            timeoutAt = 0;
        }
    }

    /**
     * 鍙栨秷鏈粍浠剁殑璋冪敤
     */
    public void cancel() {
        if (markFinished()) {
            canceled = true;
            setResult4Waiting(CCResult.error(CCResult.CODE_ERROR_CANCELED));
            verboseLog(callId, "call cancel()");
        } else {
            verboseLog(callId, "call cancel(). but this cc is already finished");
        }
    }

    boolean isFinished() {
        return finished.get();
    }

    private boolean markFinished() {
        return finished.compareAndSet(false, true);
    }

    public static void cancel(String callId) {
        verboseLog(callId, "call CC.cancel()");
        CC cc = CCMonitor.getById(callId);
        if (cc != null) {
            cc.cancel();
        }
    }
    void timeout() {
        if (markFinished()) {
            timeoutStatus = true;
            setResult4Waiting(CCResult.error(CCResult.CODE_ERROR_TIMEOUT));
            verboseLog(callId, "timeout");
        } else {
            verboseLog(callId, "call timeout(). but this cc is already finished");
        }
    }

    /**
     * 鍦ㄤ换鎰忎綅缃洖璋冪粨鏋?     * 缁勪欢鐨刼nCall鏂规硶琚皟鐢ㄥ悗锛?b>蹇呴』纭繚鎵€鏈夊垎鏀潎浼氳皟鐢?/b>鍒版鏂规硶灏嗙粍浠惰皟鐢ㄧ粨鏋滃洖璋冪粰璋冪敤鏂?     * @param callId 鍥炶皟瀵硅薄鐨勮皟鐢╥d
     * @param ccResult 鍥炶皟鐨勭粨鏋?     */
    public static void sendCCResult(String callId, CCResult ccResult) {
        if (VERBOSE_LOG) {
            verboseLog(callId, "CCResult received by CC.sendCCResult(...).CCResult:" + ccResult);
        }
        CC cc = CCMonitor.getById(callId);
        if (cc != null) {
            if (cc.markFinished()) {
                if (ccResult == null) {
                    ccResult = CCResult.defaultNullResult();
                    logError("CC.sendCCResult called, But ccResult is null, set it to CCResult.defaultNullResult(). "
                            + "ComponentName=" + cc.getComponentName());
                }
                cc.setResult4Waiting(ccResult);
            } else {
                logError("CC.sendCCResult called, But ccResult is null. "
                        + "ComponentName=" + cc.getComponentName());
            }
        } else {
            log("CCResult received, but cannot found callId:" + callId);
        }
    }

    /**
     * 鍦ㄤ换鎰忎綅缃洖璋冪粨鏋?     * @param callId 鍥炶皟瀵硅薄鐨勮皟鐢╥d
     * @param result 鍥炶皟鐨勭粨鏋?     * @deprecated use {@link #sendCCResult(String, CCResult)}
     */
    @Deprecated
    public static void invokeCallback(String callId, CCResult result) {
        sendCCResult(callId, result);
    }

    /**
     * 鑾峰彇褰撳墠app鍐呮槸鍚﹀惈鏈夋寚瀹氱殑缁勪欢
     * @param componentName 缁勪欢鍚嶇О
     * @return true:鏈夛紝 false:娌℃湁
     */
    public static boolean hasComponent(String componentName) {
        return ComponentManager.hasComponent(componentName);
    }

    /**
     * 鍔ㄦ€佹敞鍐岀粍浠?绫讳技浜庡姩鎬佹敞鍐屽箍鎾帴鏀跺櫒BroadcastReceiver)
     * @param component 缁勪欢瀵硅薄
     */
    public static void registerComponent(IDynamicComponent component) {
        ComponentManager.registerComponent(component);
    }

    /**
     * 鍔ㄦ€佸弽娉ㄥ唽缁勪欢(绫讳技浜庡弽娉ㄥ唽骞挎挱鎺ユ敹鍣˙roadcastReceiver)
     * @param component 缁勪欢瀵硅薄
     */
    public static void unregisterComponent(IDynamicComponent component) {
        ComponentManager.unregisterComponent(component);
    }

    /**
     * 娉ㄥ唽涓€涓叏灞€鎷︽埅鍣?     * 姣忎釜鎷︽埅鍣ㄧ被鍨嬪彧鑳芥敞鍐屼竴娆?     * {@link IGlobalCCInterceptor}鎺ュ彛鐨勭洿鎺ュ疄鐜扮被榛樿浼氳嚜鍔ㄦ敞鍐岋紝鏃犻渶鎵嬪姩娣诲姞
     * 鑻ヤ笉闇€瑕佽嚜鍔ㄦ敞鍐岋紝鍙娇鐢ㄥ瓙鎺ュ彛鐨勬柟寮忔潵瀹氫箟
     * @param interceptor 鍏ㄥ眬鎷︽埅鍣?     */
    public static void registerGlobalInterceptor(IGlobalCCInterceptor interceptor) {
        GlobalCCInterceptorManager.registerGlobalInterceptor(interceptor);
    }

    /**
     * 娉ㄩ攢涓€涓叏灞€鎷︽埅鍣?     * @param clazz 鎷︽埅鍣ㄧ殑绫诲瀷
     */
    public static void unregisterGlobalInterceptor(Class<? extends IGlobalCCInterceptor> clazz) {
        GlobalCCInterceptorManager.unregisterGlobalInterceptor(clazz);
    }

    private static String prefix;
    private static AtomicInteger index = new AtomicInteger(1);
    private String nextCallId() {
        if (TextUtils.isEmpty(prefix)) {
            Context context = getContext();
            if (context != null) {
                prefix = context.getPackageName() + ":";
            } else {
                return ":::" + index.getAndIncrement();
            }
        }
        return prefix + index.getAndIncrement();
    }

    static void verboseLog(String callId, String s, Object... args) {
        if (VERBOSE_LOG) {
            s = format(s, args);
            Log.i(CC.VERBOSE_TAG, "(" + Thread.currentThread().getName() + ")"
                    + callId + " >>>> " + s);
        }
    }

    static void log(String s, Object... args) {
        if (DEBUG) {
            s = format(s, args);
            Log.i(CC.TAG, s);
        }
    }
    static void logError(String s, Object... args) {
        if (DEBUG) {
            s = format(s, args);
            Log.e(CC.TAG, s);
        }
    }

    private static String format(String s, Object... args) {
        try {
            if (args != null && args.length > 0) {
                s = String.format(s, args);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        return s;
    }

    /**
     * 寮€鍏崇粍浠惰皟鐢ㄨ繃绋嬭缁嗘棩蹇楋紝榛樿涓哄叧闂姸鎬?     * @param enable 寮€鍏筹紙true锛氭樉绀鸿缁嗘棩蹇楋紝 false锛氬叧闂€傦級
     */
    public static void enableVerboseLog(boolean enable) {
        VERBOSE_LOG = enable;
    }

    /**
     * 寮€鍏砫ebug妯″紡锛堟墦鍗版棩蹇楋級锛岄粯璁や负鍏抽棴鐘舵€?     * @param enable 寮€鍏筹紙true锛氭墦寮€debug妯″紡锛?false锛氬叧闂€傞粯璁や负false锛?     */
    public static void enableDebug(boolean enable) {
        DEBUG = enable;
    }
    /**
     * 寮€鍏宠法app璋冪敤缁勪欢鏀寔锛岄粯璁や负鎵撳紑鐘舵€?     *  1. 鏌愪釜componentName褰撳墠app涓笉瀛樺湪鏃讹紝鏄惁灏濊瘯璋冪敤鍏跺畠app鐨勬缁勪欢
     *  2. 鎺ユ敹鍒拌法app璋冪敤鏃讹紝鏄惁鎵ц鏈璋冪敤
     *  3. 寤鸿浠呭湪寮€鍙戦樁娈佃皟璇曟椂鎵撳紑锛屾寮忓彂甯冩椂鍏抽棴
     * @param enable 寮€鍏筹紙true锛氫細鎵ц锛岄粯璁ゅ€间负true锛?false锛氫笉浼氾級
     */
    public static void enableRemoteCC(boolean enable) {
        RESPONSE_FOR_REMOTE_CC = enable;
        CALL_REMOTE_CC_IF_NEED = enable;
        if (enable && application != null && isMainProcess()) {
            // 鍚姩":cc"杩涚▼
            // Wake the component process for remote calls.
            Intent intent = new Intent(application.getPackageName()
                    + ".cc.action.REMOTE_CC.awake");
            application.sendBroadcast(intent);
        }
    }

    /**
     * 褰撳墠杩涚▼鏄惁浠ュ寘鍚嶅湪杩愯锛堝綋鍓嶈繘绋嬫槸鍚︿负涓昏繘绋嬶級
     */
    public static boolean isMainProcess(){
        return CCUtil.isMainProcess();
    }
}

