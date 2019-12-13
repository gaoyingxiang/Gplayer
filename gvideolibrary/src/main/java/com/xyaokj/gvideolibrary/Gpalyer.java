package com.xyaokj.gvideolibrary;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;

import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;


import com.xyaokj.gvideolibrary.utils.LightnessControl;
import com.xyaokj.gvideolibrary.utils.PlayerUtils;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import io.vov.vitamio.MediaPlayer;
import io.vov.vitamio.widget.VideoView;

public class Gpalyer extends FrameLayout implements View.OnClickListener, GestureDetector.OnGestureListener, SeekBar.OnSeekBarChangeListener {
    private Context mContext;
    private Activity mActivity;
    private static final String TAG = "视频播放器---->";
    private boolean isFirst = false;//是否打开就播放

    private VideoView mVideoView;//视频播放器

    //顶部view
    private RelativeLayout gRlTopMenu;
    private ImageView gIvBack;//返回键
    private TextView gViewName;//视频名称
    //底部view
    private RelativeLayout gRlBottomMenu;
    private ImageView gIvPlayPause;//停止播放
    private SeekBar gSeekBar;//进度条
    private TextView gTvTime;//视频时间
    private ImageView gIvFullScreen;//全屏

    private boolean isFullScreen = false;//是否是全屏
    private boolean isPrepare = false;//是否是暂停
    private boolean isCanSpeed = true;//是否可以快进
    //标记暂停和播放状态
    private boolean isPlaying = true;


    private String url;//视频播放地址
    private String videoName;
    //控件的位置信息
    private float mediaPlayerX;//
    private float mediaPlayerY;
    private int playerViewW;//播放的宽
    private int playerViewH;//播放的高
    //默认宽高比16:9
    private int defaultWidthProportion = 16;
    private int defaultHeightProportion = 9;

    private int videoViewH;//记录控件的高度，切屏的时候保持一直
    private int video_position = 0;

    static final Handler myHandler = new Handler(Looper.getMainLooper()) {
    };

    // 计时器
    private Timer timer_video_time;
    private TimerTask task_video_timer;
    private Timer timer_controller;
    private TimerTask task_controller;

    //接口
    private VideoStartInterface videoStartInterface;
    private VideoEndInterface videoEndInterface;
    private VideoCantSpeedInterface videoCantSpeedInterface;


    public void setVideoName(String videoName) {
        this.videoName = videoName;
    }

    public Gpalyer(Context context) {
        super(context);
        mContext = context;
        initView();
    }

    public Gpalyer(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        initAttrs(context, attrs);
        initView();
    }

    public Gpalyer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        initAttrs(context, attrs);
        initView();
    }

    /**
     * 读取属性
     */
    public void initAttrs(Context context, AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.MNViderPlayer);
        for (int i = 0; i < typedArray.getIndexCount(); i++) {
            int index = typedArray.getIndex(i);
            if (index == R.styleable.MNViderPlayer_mnFirstNeedPlay) {
                isFirst = typedArray.getBoolean(R.styleable.MNViderPlayer_mnFirstNeedPlay, false);//拿到第一次是否自动播放的属性
            }
        }
        //销毁
        typedArray.recycle();
    }

    /**
     * 初始化布局
     */
    public void initView() {
        View view = View.inflate(mContext, R.layout.video_layout, this);

        mVideoView = (VideoView) view.findViewById(R.id.g_video_view);
        gRlTopMenu = (RelativeLayout) view.findViewById(R.id.g_rl_top_menu);
        gIvBack = (ImageView) view.findViewById(R.id.g_iv_back);
        gViewName = (TextView) view.findViewById(R.id.g_tv_title);
        gRlBottomMenu = (RelativeLayout) view.findViewById(R.id.g_rl_bottom_menu);
        gIvPlayPause = (ImageView) view.findViewById(R.id.g_iv_play_pause);
        gSeekBar = (SeekBar) view.findViewById(R.id.g_seekBar);
        gIvFullScreen = (ImageView) view.findViewById(R.id.g_iv_fullScreen);
        gTvTime = (TextView) view.findViewById(R.id.g_tv_time);

        initTopView();
        initBottomView();
        initViewEvent();

        //初始化播放监听
        initPlayerListener();
        mActivity = (Activity) mContext;
        //存储控件位置
        saveViewLayout();
        //初始化获取视频实时播放进度
        initTimeTask();
        //初始化手势
        initGesture();
    }


    /**
     * 播放器初始化
     */
    private void initPlayerListener() {
        //视频预处理回调  （此时视频的宽度、高度、宽高比信息已经获取到，此时可调用seekTo让视频从指定位置开始播放。）
        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(final MediaPlayer mp) {
                //是否开始播放
                if (!isPlaying) {
                    mp.pause();
                    gIvPlayPause.setImageResource(R.drawable.g_player_play);
                } else {
                    gIvPlayPause.setImageResource(R.drawable.g_player_pause);
                }
                gViewName.setText(videoName);

                gSeekBar.setMax((int) mp.getDuration());//设置总长度
                //播放的时间/总的时间
                gTvTime.setText(String.valueOf(PlayerUtils.converLongTimeToStr(mp.getCurrentPosition()) + "/" + PlayerUtils.converLongTimeToStr(mp.getDuration())));
                mp.setBufferSize(10240); //设置视频缓冲大小。默认1024KB，单位byte

                //延时：避免出现上一个视频的画面闪屏
                myHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        initBottomMenuState();
                        //跳转指定位置
                        if (video_position > 0) {
                            Log.i(TAG, "onPrepared---video_position:" + video_position);
                            mp.seekTo(video_position);
                            video_position = 0;
                        }
                    }
                }, 500);

                if (videoStartInterface != null) {
                    videoStartInterface.start(mp);
                }
                Log.e("播放了--->", "-------------->");
//                fitVideoScreenSize();
            }
        });

        //注册一个回调函数，在网络视频流缓冲变化时调用。
        mVideoView.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(MediaPlayer mp, int percent) {
                Log.i(TAG, "二级缓存onBufferingUpdate: " + percent);
                if (percent >= 0 && percent <= 100) {
                    int secondProgress = (int) (mp.getDuration() * percent / 100);
                    gSeekBar.setSecondaryProgress(secondProgress);
                }
            }
        });

        //注册一个回调函数，在有警告或错误信息时调用。例如：开始缓冲、缓冲结束、下载速度变化。
        mVideoView.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                switch (what) {
                    //开始缓冲
                    case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                        gLoadingLayout.setVisibility(View.VISIBLE);
                        mp.pause();
                        break;
                    //缓冲结束
                    case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                        gLoadingLayout.setVisibility(View.GONE);
                        mp.start(); //缓冲结束再播放
                        break;
                    //正在缓冲
                    case MediaPlayer.MEDIA_INFO_DOWNLOAD_RATE_CHANGED:
                        tvPress.setText(extra + "kb/s");
                        break;
                }
                return true;
            }
        });

        //注册一个回调函数，视频播放完成后调用
        mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                pause();
                isPlaying = false;
                video_position = 0;
                mp.seekTo(video_position);
                if (videoEndInterface != null) {
                    videoEndInterface.end();
                }
            }
        });

        //注册一个回调函数，在异步操作调用过程中发生错误时调用。例如视频打开失败。
        mVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                return false;
            }
        });
    }


    /**
     * 初始化顶部
     */
    private void initTopView() {
        gIvBack.setOnClickListener(this);
        gRlTopMenu.setVisibility(GONE);
    }

    /**
     * 初始化底部
     */
    private void initBottomView() {
        gIvPlayPause.setOnClickListener(this);
        gIvFullScreen.setOnClickListener(this);
        gSeekBar.setOnSeekBarChangeListener(this);
        gRlBottomMenu.setVisibility(GONE);
    }

    /**
     *
     */
    private void initViewEvent() {

    }

    //----------------------------------------------------  按钮操作 -----------------------------------------------------------------

    @Override
    public void onClick(View view) {
        int id = view.getId();
        //是否暂停
        if (id == R.id.g_iv_play_pause) {
            if (mVideoView != null) {
                if (mVideoView.isPlaying()) {
                    gIvPlayPause.setImageResource(R.drawable.g_player_play);
                    isPrepare = true;
                    isPlaying = false;
                    pause();
                } else {
                    gIvPlayPause.setImageResource(R.drawable.g_player_pause);
                    isPrepare = false;
                    isPlaying = true;
                    play();
                }
            }

        }
        //是否全屏
        if (id == R.id.g_iv_fullScreen) {
            if (isFullScreen) {
                setProtrait();
                gIvFullScreen.setImageResource(R.drawable.g_player_ic_fullscreen);
            } else {
                setLandscape();
                gIvFullScreen.setImageResource(R.drawable.g_player_ic_fullscreen_exit);
            }
        }
        //全屏返回键
        if (id == R.id.g_iv_back) {
            if (isFullScreen) {
                setProtrait();
                gIvFullScreen.setImageResource(R.drawable.g_player_ic_fullscreen);
            }
        }
    }

    //------------------------------------------------  传参设置 ----------------------------------------------------------------
    public VideoView getGplayer() {
        if (mVideoView == null) {
            mVideoView = new VideoView(mContext);
        } else {
            return mVideoView;
        }
        return mVideoView;
    }

    public void setVideoDataSource(Uri uri) {
        mVideoView.setVideoURI(uri);
    }

    public void setVideoDataSource(Uri uri, String videoTitle) {
        mVideoView.setVideoURI(uri);
        videoName = videoTitle;
    }

    public void setVideoDataSource(Uri uri, String videoTitle, Map<String, String> headers) {
        mVideoView.setVideoURI(uri, headers);
        videoName = videoTitle;
    }

    public void setVideoLocaPath(String videoPath) {
        mVideoView.setVideoPath(videoPath);
    }

    //------------------------------------------------- 视频播放相关 ------------------------------------------------------------

    /**
     * 播放
     */
    public void start() {
        mVideoView.start();
    }

    /**
     * 指定位置播放
     */
    public void startAtPosition(int position) {
        mVideoView.start();
        video_position = position;
    }

    /**
     * 暂停
     */
    public void pause() {
        mVideoView.pause();
        gIvPlayPause.setImageResource(R.drawable.g_player_play);
        isPrepare = true;
        isPlaying = false;
    }

    /**
     * 播放
     */
    public void play() {
        mVideoView.start();
        gIvPlayPause.setImageResource(R.drawable.g_player_pause);
        isPrepare = false;
        isPlaying = true;
    }

    public void destroyVideo() {
        if (mVideoView != null) {
            mVideoView = null;
        }
        video_position = 0;
        removeAllListener();
        destroyTimeTask();
        myHandler.removeCallbacksAndMessages(null);
    }

    //---------------------------------------------------------  其他方法 ------------------------------------------------------------

    /**
     * 设置横屏
     */
    public void setLandscape() {
        isFullScreen = true;
        //设置横屏
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        gRlBottomMenu.setVisibility(View.VISIBLE);
        gRlTopMenu.setVisibility(View.VISIBLE);
    }

    /**
     * 设置竖排
     */
    public void setProtrait() {
        isFullScreen = false;
        //设置横屏
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        gRlTopMenu.setVisibility(View.GONE);
    }

    /**
     * 存储控件的位置，横竖屏切换的时候不会改变位置
     */
    public void saveViewLayout() {
        //存储控件的位置信息
        myHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mediaPlayerX = getX();
                mediaPlayerY = getY();
                videoViewH = mVideoView.getLayoutParams().height;

                playerViewW = getWidth();
                playerViewH = getHeight();

                Log.i(TAG, "控件信息---X：" + mediaPlayerX + "，Y：" + mediaPlayerY);
                Log.i(TAG, "视频播放器---X：" + playerViewW + "，Y：" + videoViewH);

                Log.i(TAG, "初始视频播放器---X：" + playerViewW + "，Y：" + playerViewH);

            }
        }, 1000);
    }

    private void removeAllListener() {
        if (videoStartInterface != null) {
            videoStartInterface = null;
        }
        if (videoEndInterface != null) {
            videoEndInterface = null;
        }

        if (videoCantSpeedInterface != null) {
            videoCantSpeedInterface = null;
        }
    }

    //--------------------------------------------------------  横竖屏切换回调 ------------------------------------------------------

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
//        super.onConfigurationChanged(newConfig);
//
//        int screenWidth = PlayerUtils.getScreenWidth(mActivity);
//        int screenHeight = PlayerUtils.getScreenHeight(mActivity);
//        ViewGroup.LayoutParams layoutParams = getLayoutParams();
//        Log.e("屏幕的高宽-->", "\n" + "高：" + screenHeight + "\n" + "宽：" + screenWidth);
//        //表示竖向
//        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
//            mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
//            layoutParams.width = screenWidth;
//            layoutParams.height = videoViewH;
//
//            playerViewW = screenWidth;
//            playerViewH = videoViewH;
//            Log.e("竖屏--视频的高宽-->", "\n" + "高：" + playerViewH + "\n" + "宽：" + playerViewW);
//
//        }
//        // 表示横屏
//        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
//            mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
//            layoutParams.width = screenWidth;
//            layoutParams.height = screenHeight;
//
//            playerViewW = screenWidth;
//            playerViewH = screenHeight;
//
//            Log.e("横屏--视频的高宽-->", "\n" + "高：" + playerViewH + "\n" + "宽：" + playerViewW);
//
//        }
//        //修复视频的大小
//


        int screenWidth = PlayerUtils.getScreenWidth(mActivity);
        int screenHeight = PlayerUtils.getScreenHeight(mActivity);
        ViewGroup.LayoutParams layoutParams = getLayoutParams();

        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {

            mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            //计算视频的大小16：9
            layoutParams.width = screenWidth;
            layoutParams.height = screenWidth * defaultHeightProportion / defaultWidthProportion;
            setX(mediaPlayerX);
            setY(mediaPlayerY);
        }
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {

            mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            layoutParams.width = screenWidth;
            layoutParams.height = screenHeight;

            setX(0);
            setY(0);
        }
        setLayoutParams(layoutParams);

        playerViewW = screenWidth;
        playerViewH = layoutParams.height;
        fitVideoScreenSize();
    }

    /**
     * 横竖屏切换视频修复
     */
    private void fitVideoScreenSize() {
//        ViewGroup.LayoutParams lp = mVideoView.getLayoutParams();
//        lp.width = playerViewW;
//        lp.height = playerViewH;
//        mVideoView.setLayoutParams(lp);

        if (mVideoView == null) {
            return;
        }
        //适配视频的高度
        int videoWidth = mVideoView.getVideoWidth();
        int videoHeight = mVideoView.getVideoHeight();
        int parentWidth = playerViewW;
        int parentHeight = playerViewH;
        int screenWidth = PlayerUtils.getScreenWidth(mActivity);
        int screenHeight = PlayerUtils.getScreenHeight(mActivity);

        //判断视频宽高和父布局的宽高
        int surfaceViewW;
        int surfaceViewH;
        if ((float) videoWidth / (float) videoHeight > (float) parentWidth / (float) parentHeight) {
            surfaceViewW = parentWidth;
            surfaceViewH = videoHeight * surfaceViewW / videoWidth;
        } else {
            surfaceViewH = parentHeight;
            surfaceViewW = videoWidth * parentHeight / videoHeight;
        }

        Log.i(TAG, "fitVideoSize---" +
                "videoWidth：" + videoWidth + ",videoHeight:" + videoHeight +
                ",parentWidth:" + parentWidth + ",parentHeight:" + parentHeight +
                ",screenWidth:" + screenWidth + ",screenHeight:" + screenHeight +
                ",surfaceViewW:" + surfaceViewW + ",surfaceViewH:" + surfaceViewH
        );
        //改变surfaceView的大小
        ViewGroup.LayoutParams params = mVideoView.getLayoutParams();
        params.height = surfaceViewH;
        params.width = surfaceViewW;
        mVideoView.setLayoutParams(params);
    }

    //------------------------------------------计时器相关操作------------------------------------------------------------

    private void initTimeTask() {
        timer_video_time = new Timer();
        task_video_timer = new TimerTask() {
            @Override
            public void run() {
                myHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mVideoView == null) {
                            return;
                        }
                        //设置时间
                        gTvTime.setText(String.valueOf(PlayerUtils.converLongTimeToStr(mVideoView.getCurrentPosition()) + " / " + PlayerUtils.converLongTimeToStr(mVideoView.getDuration())));
                        //进度条
                        int progress = (int) mVideoView.getCurrentPosition();
                        //playPosition = mediaPlayer.getCurrentPosition();
                        gSeekBar.setProgress(progress);
                    }
                });
            }
        };
        timer_video_time.schedule(task_video_timer, 0, 1000);
    }


    //-------------------------------------------------------  手势部分 ---------------------------------------------------------
    private RelativeLayout gesture_volume_layout;// 音量控制布局
    private TextView geture_tv_volume_percentage;// 音量百分比
    private ImageView gesture_iv_player_volume;// 音量图标
    private RelativeLayout gesture_light_layout;// 亮度布局
    private TextView geture_tv_light_percentage;// 亮度百分比
    private RelativeLayout gesture_progress_layout;// 进度图标
    private TextView geture_tv_progress_time;// 播放时间进度
    private ImageView gesture_iv_progress;// 快进或快退标志
    private GestureDetector gestureDetector;//手势
    private AudioManager audiomanager;//音频管理器
    private int maxVolume, currentVolume;
    private static final float STEP_PROGRESS = 2f;// 设定进度滑动时的步长，避免每次滑动都改变，导致改变过快
    private static final float STEP_VOLUME = 2f;// 协调音量滑动时的步长，避免每次滑动都改变，导致改变过快
    private static final float STEP_LIGHT = 2f;// 协调亮度滑动时的步长，避免每次滑动都改变，导致改变过快
    private int GESTURE_FLAG = 0;// 1,调节进度，2，调节音量
    private static final int GESTURE_MODIFY_PROGRESS = 1;
    private static final int GESTURE_MODIFY_VOLUME = 2;
    private static final int GESTURE_MODIFY_BRIGHTNESS = 3;
    private static final int GESTURE_MODIFY_NOT_MOVE = 4;//给出提示，不能移动

    private RelativeLayout gLoadingLayout;
    private TextView tvPress;


    /**
     * 初始化 手势操作
     */
    private void initGesture() {
        //音量布局
        gesture_volume_layout = (RelativeLayout) findViewById(R.id.g_gesture_volume_layout);//音量
        geture_tv_volume_percentage = (TextView) findViewById(R.id.g_gesture_tv_volume_percentage); //音量百分比
        gesture_iv_player_volume = (ImageView) findViewById(R.id.g_gesture_iv_player_volume);//音量图标

        //进度布局
        gesture_progress_layout = (RelativeLayout) findViewById(R.id.g_gesture_progress_layout);// 进度图标
        geture_tv_progress_time = (TextView) findViewById(R.id.g_gesture_tv_progress_time);// 播放时间进度
        gesture_iv_progress = (ImageView) findViewById(R.id.g_gesture_iv_progress);// 快进或快退标志

        //亮度的布局
        gesture_light_layout = (RelativeLayout) findViewById(R.id.g_gesture_light_layout);// 亮度布局
        geture_tv_light_percentage = (TextView) findViewById(R.id.g_geture_tv_light_percentage);// 亮度百分比

        //初始化进度布局
        gLoadingLayout = (RelativeLayout) findViewById(R.id.g_loading_layout);
        tvPress = (TextView) findViewById(R.id.tv_press);

        gesture_volume_layout.setVisibility(View.GONE);// 音量控制布局  --初始化不可见
        gesture_progress_layout.setVisibility(View.GONE);// 进度图标  --初始化不可见
        gesture_light_layout.setVisibility(View.GONE);// 亮度布局  --初始化不可见

        gestureDetector = new GestureDetector(getContext(), this);//手势
        setLongClickable(true);
        gestureDetector.setIsLongpressEnabled(true);

        audiomanager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        maxVolume = audiomanager.getStreamMaxVolume(AudioManager.STREAM_MUSIC); // 获取系统最大音量
        currentVolume = audiomanager.getStreamVolume(AudioManager.STREAM_MUSIC); // 获取当前值
    }

    @Override
    public boolean onDown(MotionEvent motionEvent) {
        return true;
    }

    @Override
    public void onShowPress(MotionEvent motionEvent) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent motionEvent) {
        Log.e(TAG, "OnSingleTapUp");
        initBottomMenuState();
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float x, float y) {
        if (isPrepare) {
            return false;
        }
        int FLAG = 0;

        if (isCanSpeed) {//是否可以快进
            // 横向的距离变化大则调整进度，纵向的变化大则调整音量
            if (Math.abs(x) >= Math.abs(y)) {
                if (mVideoView != null && mVideoView.isPlaying()) {
                    //标记的是进度
                    FLAG = GESTURE_MODIFY_PROGRESS;
                }
            } else {
                int intX = (int) motionEvent.getX();
                int screenWidth = PlayerUtils.getScreenWidth(mActivity);
                if (intX > screenWidth / 2) {
                    FLAG = GESTURE_MODIFY_VOLUME;
                } else {
                    //左边是亮度
                    FLAG = GESTURE_MODIFY_BRIGHTNESS;
                }
            }

        } else { // 表示的是没有看完
            // 横向的距离变化大则调整进度，纵向的变化大则调整音量
            if (Math.abs(x) >= Math.abs(y)) {
                if (x >= PlayerUtils.dip2px(mContext, STEP_PROGRESS)) {// 快退，
                    FLAG = GESTURE_MODIFY_PROGRESS;//快退是可以的
                } else if (x <= -PlayerUtils.dip2px(mContext, STEP_PROGRESS)) {// 快进
                    FLAG = GESTURE_MODIFY_NOT_MOVE;//不能移动
                }
            } else {
                int intX = (int) motionEvent.getX();
                int screenWidth = PlayerUtils.getScreenWidth(mActivity);
                if (intX > screenWidth / 2) {
                    FLAG = GESTURE_MODIFY_VOLUME;
                } else {
                    //左边是亮度
                    FLAG = GESTURE_MODIFY_BRIGHTNESS;
                }
            }
        }

        if (GESTURE_FLAG != 0 && GESTURE_FLAG != FLAG) {
            return false;
        }

        GESTURE_FLAG = FLAG;

        if (FLAG == GESTURE_MODIFY_PROGRESS) {
            //表示是横向滑动,可以添加快进
            // x=lastScrollPositionX-currentScrollPositionX，因此为正时是快进
            gesture_volume_layout.setVisibility(View.GONE);
            gesture_light_layout.setVisibility(View.GONE);
            gesture_progress_layout.setVisibility(View.VISIBLE);
            try {
                if (mVideoView != null && mVideoView.isPlaying()) {
                    if (Math.abs(x) > Math.abs(y)) {// 横向移动大于纵向移动
                        if (x >= PlayerUtils.dip2px(mContext, STEP_PROGRESS)) {// 快退，用步长控制改变速度，可微调
                            gesture_iv_progress
                                    .setImageResource(R.drawable.g_player_backward);
                            if (mVideoView.getCurrentPosition() > 3 * 1000) {// 避免为负
                                int cpos = (int) mVideoView.getCurrentPosition();
                                mVideoView.seekTo(cpos - 3000);
                                gSeekBar.setProgress((int) mVideoView.getCurrentPosition());
                            } else {
                                //什么都不做
                                mVideoView.seekTo(3000);
                            }
                        } else if (x <= -PlayerUtils.dip2px(mContext, STEP_PROGRESS)) {// 快进
                            gesture_iv_progress
                                    .setImageResource(R.drawable.g_player_forward);
                            if (mVideoView.getCurrentPosition() < mVideoView.getDuration() - 5 * 1000) {// 避免超过总时长
                                int cpos = (int) mVideoView.getCurrentPosition();
                                mVideoView.seekTo(cpos + 3000);
                                // 把当前位置赋值给进度条
                                gSeekBar.setProgress((int) mVideoView.getCurrentPosition());
                            }
                        }
                    }
                    String timeStr = PlayerUtils.converLongTimeToStr(mVideoView.getCurrentPosition()) + " / "
                            + PlayerUtils.converLongTimeToStr(mVideoView.getDuration());
                    geture_tv_progress_time.setText(timeStr);

                }
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }
        // 如果每次触摸屏幕后第一次scroll是调节音量，那之后的scroll事件都处理音量调节，直到离开屏幕执行下一次操作
        else if (FLAG == GESTURE_MODIFY_VOLUME) {
            //右边是音量
            gesture_volume_layout.setVisibility(View.VISIBLE);
            gesture_light_layout.setVisibility(View.GONE);
            gesture_progress_layout.setVisibility(View.GONE);
            currentVolume = audiomanager
                    .getStreamVolume(AudioManager.STREAM_MUSIC); // 获取当前值
            if (Math.abs(y) > Math.abs(x)) {// 纵向移动大于横向移动
                if (currentVolume == 0) {// 静音，设定静音独有的图片
                    gesture_iv_player_volume.setImageResource(R.drawable.g_player_volume_close);
                }
                if (y >= PlayerUtils.dip2px(mContext, STEP_VOLUME)) {// 音量调大,注意横屏时的坐标体系,尽管左上角是原点，但横向向上滑动时y为正
                    if (currentVolume < maxVolume) {// 为避免调节过快，y应大于一个设定值
                        currentVolume++;
                    }
                    gesture_iv_player_volume.setImageResource(R.drawable.g_player_volume_open);
                } else if (y <= -PlayerUtils.dip2px(mContext, STEP_VOLUME)) {// 音量调小
                    if (currentVolume > 0) {
                        currentVolume--;
                        if (currentVolume == 0) {// 静音，设定静音独有的图片
                            gesture_iv_player_volume.setImageResource(R.drawable.g_player_volume_close);
                        }
                    }
                }
                int percentage = (currentVolume * 100) / maxVolume;
                geture_tv_volume_percentage.setText(String.valueOf(percentage + "%"));
                audiomanager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0);
            }
        }
        //调节亮度
        else if (FLAG == GESTURE_MODIFY_BRIGHTNESS) {
            gesture_volume_layout.setVisibility(View.GONE);
            gesture_light_layout.setVisibility(View.VISIBLE);
            gesture_progress_layout.setVisibility(View.GONE);
            currentVolume = audiomanager
                    .getStreamVolume(AudioManager.STREAM_MUSIC); // 获取当前值
            if (Math.abs(y) > Math.abs(x)) {// 纵向移动大于横向移动
                // 亮度调大,注意横屏时的坐标体系,尽管左上角是原点，但横向向上滑动时distanceY为正
                int mLight = LightnessControl.GetLightness((Activity) mContext);
                if (mLight >= 0 && mLight <= 255) {
                    if (y >= PlayerUtils.dip2px(mContext, STEP_LIGHT)) {
                        if (mLight > 245) {
                            LightnessControl.SetLightness((Activity) mContext, 255);
                        } else {
                            LightnessControl.SetLightness((Activity) mContext, mLight + 10);
                        }
                    } else if (y <= -PlayerUtils.dip2px(mContext, STEP_LIGHT)) {// 亮度调小
                        if (mLight < 10) {
                            LightnessControl.SetLightness((Activity) mContext, 0);
                        } else {
                            LightnessControl.SetLightness((Activity) mContext, mLight - 10);
                        }
                    }
                } else if (mLight < 0) {
                    LightnessControl.SetLightness((Activity) mContext, 0);
                } else {
                    LightnessControl.SetLightness((Activity) mContext, 255);
                }
                //获取当前亮度
                int currentLight = LightnessControl.GetLightness((Activity) mContext);
                int percentage = (currentLight * 100) / 255;
                geture_tv_light_percentage.setText(String.valueOf(percentage + "%"));
            }
        }
        //不能操作(不能快进)
        else if (FLAG == GESTURE_MODIFY_NOT_MOVE) {
            if (videoCantSpeedInterface != null) {
                videoCantSpeedInterface.cant();
            } else {
                Log.e("设置不允许快进后，请实现--->", "videoCantSpeedInterface接口");
            }
            return false;
        }
        return false;
    }

    @Override
    public void onLongPress(MotionEvent motionEvent) {

    }

    @Override
    public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 手势里除了singleTapUp，没有其他检测up的方法
        if (event.getAction() == MotionEvent.ACTION_UP) {
            GESTURE_FLAG = 0;// 手指离开屏幕后，重置调节音量或进度的标志
            gesture_volume_layout.setVisibility(View.GONE);
            gesture_progress_layout.setVisibility(View.GONE);
            gesture_light_layout.setVisibility(View.GONE);
        }
        return gestureDetector.onTouchEvent(event);
    }

    //---------------------------------------  控制菜单的显示与隐藏 ------------------------------------------------------
    //下面菜单的显示和隐藏
    private void initBottomMenuState() {
        if (gRlBottomMenu.getVisibility() == View.GONE) {//如果底部导航栏是消失
            initControllerTask();//对时间操作
            gRlBottomMenu.setVisibility(View.VISIBLE);//底部操作栏 --显示
            if (isFullScreen) {//如果是全屏，则底部和顶部都显示
                gRlTopMenu.setVisibility(View.VISIBLE);
            }
        } else { //如果底部导航栏显示了
            destroyControllerTask(true);
        }
    }

    private void initControllerTask() {
        // 设置计时器,控制器的影藏和显示
        timer_controller = new Timer();
        task_controller = new TimerTask() {
            @Override
            public void run() {
                destroyControllerTask(false);
            }
        };
        timer_controller.schedule(task_controller, 5000);
        initTimeTask();
    }

    private void destroyControllerTask(boolean isMainThread) {
        if (isMainThread) {
            dismissControllerMenu();
        } else {
            myHandler.post(new Runnable() {
                @Override
                public void run() {
                    dismissControllerMenu();
                }
            });
        }
        if (timer_controller != null && task_controller != null) {
            timer_controller.cancel();
            task_controller.cancel();
            timer_controller = null;
            task_controller = null;
        }
        destroyTimeTask();
    }

    private void dismissControllerMenu() {
//        if (isFullScreen && !isLockScreen) {
//            mn_player_iv_lock.setVisibility(View.GONE);
//        }
        gRlTopMenu.setVisibility(View.GONE);
        gRlBottomMenu.setVisibility(View.GONE);
    }

    private void destroyTimeTask() {
        if (timer_video_time != null && task_video_timer != null) {
            timer_video_time.cancel();
            task_video_timer.cancel();
            timer_video_time = null;
            task_video_timer = null;
        }
    }

    //---------------------------------------------------  进度条拖动相关 ------------------------------------------------------
    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (mVideoView != null && mVideoView.isPlaying()) {
            int maxCanSeekTo = seekBar.getMax() - 5 * 1000;
            if (seekBar.getProgress() < maxCanSeekTo) {
                if (seekBar.getProgress() < mVideoView.getCurrentPosition()) {
                    mVideoView.seekTo(seekBar.getProgress());
                } else {//否则表示往前拖
                    if (isCanSpeed) {//如果已经播完了
                        mVideoView.seekTo(seekBar.getProgress());
                    } else {
                        if (videoCantSpeedInterface != null) {
                            videoCantSpeedInterface.cant();
                        } else {
                            Log.e("设置不允许快进后，请实现--->", "videoCantSpeedInterface接口");
                        }
                    }
                }
            } else {
                //不能拖到最后
                mVideoView.seekTo(maxCanSeekTo);
            }
        }
    }


    //--------------------------------------  所有视频播放器回调接口-----------------------------------------------------------

    //开始播放回调接口
    public interface VideoStartInterface {
        void start(MediaPlayer mp);
    }

    //视频播放结束
    public interface VideoEndInterface {
        void end();
    }

    //不允许快进的提示回调
    public interface VideoCantSpeedInterface {
        void cant();
    }

    public void setVideoStartInterface(VideoStartInterface videoStartInterface) {
        this.videoStartInterface = videoStartInterface;
    }

    public void setVideoEndInterface(VideoEndInterface videoEndInterface) {
        this.videoEndInterface = videoEndInterface;
    }

    public void setVideoCantSpeedInterface(VideoCantSpeedInterface videoCantSpeedInterface) {
        this.videoCantSpeedInterface = videoCantSpeedInterface;
    }
}
