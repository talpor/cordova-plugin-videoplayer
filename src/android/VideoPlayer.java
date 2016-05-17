package com.moust.cordova.videoplayer;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.VideoView;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class VideoPlayer extends CordovaPlugin implements OnCompletionListener, OnPreparedListener, OnErrorListener, OnDismissListener {

    protected static final String LOG_TAG = "VideoPlayer";

    protected static final String ASSETS = "/android_asset/";

    private CallbackContext callbackContext = null;

    private Dialog dialog;

    private VideoView videoView;

    private Button closeButton;

    private MediaPlayer player;

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action        The action to execute.
     * @param args          JSONArray of arguments for the plugin.
     * @param callbackId    The callback id used when calling back into JavaScript.
     * @return              A PluginResult object with a status and message.
     */
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("play")) {
            this.callbackContext = callbackContext;

            CordovaResourceApi resourceApi = webView.getResourceApi();
            String target = args.getString(0);
            final JSONObject options = args.getJSONObject(1);

            String fileUriStr;
            try {
                Uri targetUri = resourceApi.remapUri(Uri.parse(target));
                fileUriStr = targetUri.toString();
            } catch (IllegalArgumentException e) {
                fileUriStr = target;
            }

            Log.v(LOG_TAG, fileUriStr);

            final String path = stripFileProtocol(fileUriStr);

            // Create dialog in new thread
            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    openVideoDialog(path, options);
                }
            });

            // Don't return any result now
            PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);
            callbackContext = null;

            return true;
        }
        else if (action.equals("close")) {
            return endPlayback(callbackContext);
        }
        return false;
    }

    private boolean endPlayback(CallbackContext callbackContext) {
        if (dialog != null) {
            if(player.isPlaying()) {
                player.stop();
            }
            player.release();
            dialog.dismiss();
        }

        if (callbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK);
            result.setKeepCallback(false); // release status callback in JS side
            callbackContext.sendPluginResult(result);
            callbackContext = null;
        }

        return true;
    }

    private boolean pausePlayback(CallbackContext callbackContext) {
        if (dialog != null) {
            if(player.isPlaying()) {
                player.pause();
            }
        }

        if (callbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
            callbackContext = null;
        }

        return true;
    }

    private boolean startPlayback(CallbackContext callbackContext) {
        if (dialog != null) {
            if(!player.isPlaying()) {
                player.start();
            }
        }

        if (callbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
            callbackContext = null;
        }

        return true;
    }

    private void seekPlayback(int msecs){
        if (dialog != null) {
            int position = player.getCurrentPosition();
            player.seekTo(position + msecs);
        }
    }

    private boolean isPlaying() {
        if (dialog != null) {
            return player.isPlaying();
        }
        return false;
    }

    public int pxToDp(int px) {
        DisplayMetrics displayMetrics = cordova.getActivity().getResources().getDisplayMetrics();
        int dp = Math.round(px / (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
        return dp;
    }

    public int dpToPx(int dp) {
        DisplayMetrics displayMetrics = cordova.getActivity().getResources().getDisplayMetrics();
        int px = Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
        return px;
    }

    /**
     * Removes the "file://" prefix from the given URI string, if applicable.
     * If the given URI string doesn't have a "file://" prefix, it is returned unchanged.
     *
     * @param uriString the URI string to operate on
     * @return a path without the "file://" prefix
     */
    public static String stripFileProtocol(String uriString) {
        if (uriString.startsWith("file://")) {
            return Uri.parse(uriString).getPath();
        }
        return uriString;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected void openVideoDialog(String path, JSONObject options) {
        // Let's create the main dialog
        dialog = new Dialog(cordova.getActivity(), android.R.style.Theme_NoTitleBar);
        dialog.getWindow().getAttributes().windowAnimations = android.R.style.Animation_Dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setOnDismissListener(this);

        // Main container layout
        RelativeLayout main = new RelativeLayout(cordova.getActivity());
        main.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        main.setHorizontalGravity(Gravity.CENTER_HORIZONTAL);
        main.setVerticalGravity(Gravity.CENTER_VERTICAL);

        videoView = new VideoView(cordova.getActivity());
        videoView.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        // videoView.setVideoURI(uri);
        // videoView.setVideoPath(path);
        main.addView(videoView);

        //CloseFrame
        FrameLayout closeFrame = new FrameLayout(cordova.getActivity());
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        closeFrame.setLayoutParams(params);

        try {
            ImageButton closeButton = new ImageButton(cordova.getActivity());
            RelativeLayout.LayoutParams closeButtonParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            closeButtonParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            closeButton.setLayoutParams(closeButtonParams);
            closeButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            closeButton.setBackground(null);
            Resources res = cordova.getActivity().getResources();
            Bitmap bmp = BitmapFactory.decodeStream(res.getAssets().open("www/assets/images/videoclose.png"));
            Bitmap b = Bitmap.createScaledBitmap(bmp, dpToPx(75), dpToPx(75), true);
            closeButton.setImageBitmap(b);

            //Drawable d = Drawable.createFromStream(res.getAssets().open("www/assets/images/videoclose.png"), null);
            //closeButton.setImageDrawable(d);

            closeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(LOG_TAG, "closing video from native UI");
                    endPlayback(null);
                }
            });
            closeFrame.addView(closeButton);
        } catch (IOException e) {
            e.printStackTrace();
        }

        main.addView(closeFrame);

        // VideoControlsFrame
        RelativeLayout controlsFrame = new RelativeLayout(cordova.getActivity());
        RelativeLayout.LayoutParams controlParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        controlParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        // controlParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        controlsFrame.setLayoutParams(controlParams);
        controlsFrame.setGravity(Gravity.CENTER);

        ImageButton playButton = new ImageButton(cordova.getActivity());
        try {
            RelativeLayout.LayoutParams playButtonParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            playButtonParams.addRule(RelativeLayout.CENTER_IN_PARENT);
            playButton.setLayoutParams(playButtonParams);
            playButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            playButton.setBackground(null);
            Resources res = cordova.getActivity().getResources();
            Bitmap bmp = BitmapFactory.decodeStream(res.getAssets().open("www/assets/images/videopause.png"));
            Bitmap b = Bitmap.createScaledBitmap(bmp, dpToPx(75), dpToPx(75), true);
            playButton.setImageBitmap(b);

            playButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Resources res = cordova.getActivity().getResources();
                    try{
                        if(isPlaying()){
                            Bitmap bmp = BitmapFactory.decodeStream(res.getAssets().open("www/assets/images/videoplay.png"));
                            Bitmap b = Bitmap.createScaledBitmap(bmp, dpToPx(75), dpToPx(75), true);
                            ((ImageButton)v).setImageBitmap(b);
                            Log.d(LOG_TAG, "pausing video from native UI");
                            pausePlayback(null);
                        }else{
                            Bitmap bmp = BitmapFactory.decodeStream(res.getAssets().open("www/assets/images/videopause.png"));
                            Bitmap b = Bitmap.createScaledBitmap(bmp, dpToPx(75), dpToPx(75), true);
                            ((ImageButton)v).setImageBitmap(b);
                            Log.d(LOG_TAG, "pausing video from native UI");
                            startPlayback(null);
                        }
                    } catch (IOException e){
                        e.printStackTrace();
                    }
                }
            });
            controlsFrame.addView(playButton);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            ImageButton seekForwardButton = new ImageButton(cordova.getActivity());
            RelativeLayout.LayoutParams seekForwardParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            seekForwardParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            //seekForwardParams.setMargins(20, 0, 0, 0);
            seekForwardButton.setLayoutParams(seekForwardParams);
            seekForwardButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            seekForwardButton.setBackground(null);
            Resources res = cordova.getActivity().getResources();
            Bitmap bmp = BitmapFactory.decodeStream(res.getAssets().open("www/assets/images/video20sec.png"));
            Bitmap b = Bitmap.createScaledBitmap(bmp, dpToPx(75), dpToPx(75), true);
            seekForwardButton.setImageBitmap(b);

            seekForwardButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    seekPlayback(3000);
                }
            });
            controlsFrame.addView(seekForwardButton);

            ImageButton seekBackButton = new ImageButton(cordova.getActivity());
            RelativeLayout.LayoutParams seekBackParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            seekBackParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            //seekBackParams.setMargins(0, 0, 20, 0);
            seekBackButton.setLayoutParams(seekBackParams);
            seekBackButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            seekBackButton.setBackground(null);
            Bitmap backBmp = BitmapFactory.decodeStream(res.getAssets().open("www/assets/images/video20sec.png"));
            Bitmap backB = Bitmap.createScaledBitmap(backBmp, dpToPx(75), dpToPx(75), true);
            seekBackButton.setImageBitmap(backB);

            seekBackButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    seekPlayback(-3000);
                }
            });
            controlsFrame.addView(seekBackButton);

        } catch (IOException e) {
            e.printStackTrace();
        }


        main.addView(controlsFrame);
        player = new MediaPlayer();
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);

        if (path.startsWith(ASSETS)) {
            String f = path.substring(15);
            AssetFileDescriptor fd = null;
            try {
                fd = cordova.getActivity().getAssets().openFd(f);
                player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            } catch (Exception e) {
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getLocalizedMessage());
                result.setKeepCallback(false); // release status callback in JS side
                callbackContext.sendPluginResult(result);
                callbackContext = null;
                return;
            }
        }
        else {
            try {
                player.setDataSource(path);
            } catch (Exception e) {
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getLocalizedMessage());
                result.setKeepCallback(false); // release status callback in JS side
                callbackContext.sendPluginResult(result);
                callbackContext = null;
                return;
            }
        }

        try {
            float volume = Float.valueOf(options.getString("volume"));
            Log.d(LOG_TAG, "setVolume: " + volume);
            player.setVolume(volume, volume);
        } catch (Exception e) {
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getLocalizedMessage());
            result.setKeepCallback(false); // release status callback in JS side
            callbackContext.sendPluginResult(result);
            callbackContext = null;
            return;
        }

        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            try {
                int scalingMode = options.getInt("scalingMode");
                switch (scalingMode) {
                    case MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING:
                        Log.d(LOG_TAG, "setVideoScalingMode VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING");
                        player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                        break;
                    default:
                        Log.d(LOG_TAG, "setVideoScalingMode VIDEO_SCALING_MODE_SCALE_TO_FIT");
                        player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                }
            } catch (Exception e) {
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getLocalizedMessage());
                result.setKeepCallback(false); // release status callback in JS side
                callbackContext.sendPluginResult(result);
                callbackContext = null;
                return;
            }
        }

        final SurfaceHolder mHolder = videoView.getHolder();
        mHolder.setKeepScreenOn(true);
        mHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                player.setDisplay(holder);
                try {
                    player.prepare();
                } catch (Exception e) {
                    PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getLocalizedMessage());
                    result.setKeepCallback(false); // release status callback in JS side
                    callbackContext.sendPluginResult(result);
                    callbackContext = null;
                }
            }
            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                player.release();
            }
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;

        dialog.setContentView(main);
        dialog.show();
        dialog.getWindow().setAttributes(lp);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(LOG_TAG, "MediaPlayer.onError(" + what + ", " + extra + ")");
        if(mp.isPlaying()) {
            mp.stop();
        }
        mp.release();
        dialog.dismiss();
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mp.start();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        Log.d(LOG_TAG, "MediaPlayer completed");
        mp.release();
        dialog.dismiss();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        Log.d(LOG_TAG, "Dialog dismissed");
        if (callbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK);
            result.setKeepCallback(false); // release status callback in JS side
            callbackContext.sendPluginResult(result);
            callbackContext = null;
        }
    }
}
