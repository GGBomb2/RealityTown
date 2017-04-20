package com.example.test.app;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.location.Location;
import android.hardware.*;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.view.animation.AccelerateInterpolator;
import android.widget.*;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.PoiInfo;
import com.baidu.mapapi.search.core.RouteLine;
import com.baidu.mapapi.search.route.WalkingRouteLine;
import com.baidu.mapapi.utils.DistanceUtil;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.lang.Math;

/**
 * Created by GGBomb2 on 2015/11/6.
 */
public class SurfaceFragment extends Fragment implements SurfaceHolder.Callback{
    // UI相关
    boolean isFirstLoc = true;// 是否首次定位
    private Camera myCamera = null;  //摄像头
    private boolean isPreview = false;
    private static final String tag="yan";
    private Camera.AutoFocusCallback myAutoFocusCallback = null;//自动聚焦
    private SurfaceView mPreviewSV = null; //预览SurfaceView
    private SurfaceHolder mySurfaceHolder = null;
    private GuideFragment mGuideFragment=null;
    public boolean isGuiding=false;//是否正在导航
    RouteLine mroute=null;//路线
    Object Nextstep=null;//下一步行节点
    PoiInfo[] poiinfos=null;

    //指南针
    private static final int EXIT_TIME = 2000;// 两次按返回键的间隔判断
    private long firstExitTime = 0L;// 用来保存第一次按返回键的时间
    public String mData;
    public String address;
    private final float MAX_ROATE_DEGREE = 1.0f;// 最多旋转一周，即360°
    private SensorManager mSensorManager;// 传感器管理对象
    private Sensor mOrientationSensor;// 传感器对象
    private AccelerateInterpolator mInterpolator;// 动画从开始到结束，变化率是一个加速的过程,就是一个动画速率
    CompassView mPointer;// 指南针view
    private float mDirection;// 当前浮点方向
    private float mTargetDirection;// 目标浮点方向
    private float mLRDirection;//左右翘起角
    private float mUDDirection;//上下翘起角
    private boolean mChinease;// 系统当前是否使用中文
    protected final Handler mHandler = new Handler();
    LinearLayout mDirectionLayout;// 显示方向（东南西北）的view
    LinearLayout mAngleLayout;// 显示方向度数的view
    View mViewGuide;
    //TextView mLatitudeTV;// 纬度
    //TextView mLongitudeTV;// 经度
    View view=null;
    View mCompassView;
    ImageView mGuideAnimation;
    private boolean mStopDrawing;// 是否停止指南针旋转的标志位

    //节点指向针
    CompassView rPointer;// 节点指向针view
    private float rDirection;// 当前浮点方向
    private float rTargetDirection;// 目标浮点方向
    protected final Handler rHandler = new Handler();
    protected final Handler mOverlayHandler=new Handler();//更新图层处理
    private boolean rStopDrawing;// 是否停止节点指向针旋转的标志位


    // 这个是更新节点指向针旋转的线程，handler的灵活使用，每20毫秒检测方向变化值，对应更新节点指向针旋转
    protected Runnable rCompassViewUpdater = new Runnable() {
        @Override
        public void run() {
            if (rPointer != null && !rStopDrawing) {
                if(mroute.getAllStep()==null)
                {
                    Toast.makeText(getActivity(),"getAllstep!=null",Toast.LENGTH_SHORT);
                }
                if (mroute != null || mroute.getAllStep() != null) {
                    Nextstep=mroute.getAllStep().get(1);
                }
                if (rDirection != rTargetDirection) {

                    // calculate the short routine
                    float to = rTargetDirection;
                    if (to - rDirection > 180) {
                        to -= 360;
                    } else if (to - rDirection < -180) {
                        to += 360;
                    }
                    // limit the max speed to MAX_ROTATE_DEGREE
                    float distance = to - rDirection;
                    if (Math.abs(distance) > MAX_ROATE_DEGREE) {
                        distance = distance > 0 ? MAX_ROATE_DEGREE
                                : (-1.0f * MAX_ROATE_DEGREE);
                    }

                    // need to slow down if the distance is short
                    rDirection = normalizeDegree(rDirection
                            + ((to - rDirection) * mInterpolator
                            .getInterpolation(Math.abs(distance) > MAX_ROATE_DEGREE ? 0.4f
                                    : 0.3f)));// 用了一个加速动画去旋转图片，很细致
                    rPointer.updateDirection(rDirection);// 更新指南针旋转
                }

                //updateDirection();// 更新方向值

                rHandler.postDelayed(rCompassViewUpdater, 20);// 20毫米后重新执行自己，比定时器好
            }
        }
    };


    protected Handler invisiableHandler = new Handler() {
        public void handleMessage(Message msg) {
            mViewGuide.setVisibility(View.GONE);
        }
    };
    // 这个是更新指南针旋转的线程，handler的灵活使用，每20毫秒检测方向变化值，对应更新指南针旋转
    protected Runnable mCompassViewUpdater = new Runnable() {
        @Override
        public void run() {
            if (mPointer != null && !mStopDrawing) {
                if (mDirection != mTargetDirection) {

                    // calculate the short routine
                    float to = mTargetDirection;
                    if (to - mDirection > 180) {
                        to -= 360;
                    } else if (to - mDirection < -180) {
                        to += 360;
                    }

                    // limit the max speed to MAX_ROTATE_DEGREE
                    float distance = to - mDirection;
                    if (Math.abs(distance) > MAX_ROATE_DEGREE) {
                        distance = distance > 0 ? MAX_ROATE_DEGREE
                                : (-1.0f * MAX_ROATE_DEGREE);
                    }

                    // need to slow down if the distance is short
                    mDirection = normalizeDegree(mDirection
                            + ((to - mDirection) * mInterpolator
                            .getInterpolation(Math.abs(distance) > MAX_ROATE_DEGREE ? 0.4f
                                    : 0.3f)));// 用了一个加速动画去旋转图片，很细致
                    mPointer.updateDirection(mDirection);// 更新指南针旋转
                }

                updateDirection();// 更新方向值

                mHandler.postDelayed(mCompassViewUpdater, 20);// 20毫米后重新执行自己，比定时器好
            }
        }
    };

    // Overlay刷新，handler的灵活使用，每20毫秒检测方向变化值，对应更新指南针旋转
    protected Runnable mOverlayUpdater = new Runnable() {
        @Override
        public void run() {
            RelativeLayout raly2=(RelativeLayout)view.findViewById(R.id.relativelayout_overlay);
            raly2.removeAllViews();
            TextView tx=(TextView)view.findViewById(R.id.textView);
            tx.setText("");
            if(poiinfos!=null&&((MainActivity)getActivity()).mBaiduMap!=null) {
                int count=poiinfos.length;

                LatLng mLocation=new LatLng(((MainActivity)getActivity()).mBaiduMap.getLocationData().latitude,((MainActivity)getActivity()).mBaiduMap.getLocationData().longitude);
                for(int n=0;n<count;n++) {                                                                   //悬浮图标显示地标
                    ImageView imageView=new ImageView(getActivity());
                    imageView.setImageResource(R.drawable.ic_launcher);
                    double distance= DistanceUtil. getDistance(mLocation,poiinfos[n].location);                //获得距离
                    if(distance>1000) {                                                                       //距离大于1000米不显示
                        continue;
                    }
                    double rdirection=Math.atan(((poiinfos[n].location.latitude-mLocation.latitude))/((poiinfos[n].location.longitude-mLocation.longitude)));//获得地标与自身北向夹角
                    //normalizeDegree((((float)rdirection)-direction)*-1.0f);
                    rdirection=normalizeDegree((float)(180*rdirection/Math.PI));
                    rdirection=normalizeDegree((float)(mTargetDirection-rdirection-90.0f));
                    tx.append(Integer.toString(n)+"\t rdirection: "+Double.toString(rdirection)+"\nmTargetDirection: "+Float.toString(mTargetDirection)+"\n");
                    if(Math.abs(rdirection)>90&&Math.abs(rdirection)<270) {
                        continue;
                    }
                    int height=(int)(200-((double)3/25)*distance);
                    int width=height/3*5;
                    RelativeLayout.LayoutParams lp1=new RelativeLayout.LayoutParams(width,height);
                    lp1.topMargin=(int)((((float)1/3)-Math.cos(mUDDirection*Math.PI/180))*raly2.getHeight()-0.5f*height);                            //计算图标top
                    //if(lp1.topMargin<0) continue;
                    lp1.leftMargin=(int)((((float)1/2)+Math.sin(rdirection * Math.PI / 180))*raly2.getWidth()-0.5f*width);                           //计算图标left
                    //if(lp1.leftMargin<0) continue;
                    raly2.addView(imageView, lp1);
                }
            }
            mOverlayHandler.postDelayed(mOverlayUpdater, 20);
        }
    };

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        view = inflater.inflate(R.layout.surface_fragment, container, false);
        //初始化SurfaceView
        mPreviewSV = (SurfaceView)view.findViewById(R.id.previewSV);
        mySurfaceHolder = mPreviewSV.getHolder();
        mySurfaceHolder.setFormat(PixelFormat.TRANSLUCENT);//translucent半透明 transparent透明
        mySurfaceHolder.addCallback(this);
        mySurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        //aim图层
        //RelativeLayout raly=(RelativeLayout)view.findViewById(R.id.aimView);
        //raly.bringToFront();
        //Compass
        RelativeLayout raly2=(RelativeLayout)view.findViewById(R.id.relativelayout_overlay);
        raly2.bringToFront();
        //setContentView(R.layout.activity_main);
        initResources();// 初始化view
        initServices();// 初始化传感器和位置服务
        //
        mCompassView.bringToFront();
        //自动变焦回调
        myAutoFocusCallback=new Camera.AutoFocusCallback() {
            public void onAutoFocus(boolean success,Camera camera){
                if(success)
                {
                    Log.i(tag, "myAutoFocusCallback:success...");
                }
                else
                {
                    Log.i(tag, "myAutoFocusCallback:失败了...");
                }
            }
        };
        //添加事件
        ImageButton button_guide=(ImageButton)view.findViewById(R.id.button_guide);
        button_guide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                FragmentManager fm = getFragmentManager();
                // 开启Fragment事务
                FragmentTransaction transaction = fm.beginTransaction();
                if (mGuideFragment == null)
                {
                    mGuideFragment=new GuideFragment();
                }
                // 使用当前Fragment的布局替代id_content的控件
                transaction.replace(R.id.id_content,mGuideFragment,"GuideFragment");
                ((MainActivity)getActivity()).mGuideFragment=mGuideFragment;
                transaction.addToBackStack(null);
                transaction.commit();
            }
        });
        return view;
    }

    /*下面三个是SurfaceHolder.Callback创建的回调函数*/
    public void surfaceChanged(SurfaceHolder holder, int format, int width,int height)
    // 当SurfaceView/预览界面的格式和大小发生改变时，该方法被调用
    {
        // TODO Auto-generated method stub
        Log.i(tag, "SurfaceHolder.Callback:surfaceChanged!");
        initCamera();
        myCamera.cancelAutoFocus();
    }


    public void surfaceCreated(SurfaceHolder holder)
    // SurfaceView启动时/初次实例化，预览界面被创建时，该方法被调用。
    {
        // TODO Auto-generated method stub
        myCamera = Camera.open();
        try {
            myCamera.setPreviewDisplay(mySurfaceHolder);
            Log.i(tag, "SurfaceHolder.Callback: surfaceCreated!");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            if(null != myCamera){
                myCamera.release();
                myCamera = null;
            }
            e.printStackTrace();
        }



    }


    public void surfaceDestroyed(SurfaceHolder holder)
    //销毁时被调用
    {
        // TODO Auto-generated method stub
        Log.i(tag, "SurfaceHolder.Callback：Surface Destroyed");
        if(null != myCamera)
        {
            myCamera.setPreviewCallback(null); /*在启动PreviewCallback时这个必须在前不然退出出错。
            这里实际上注释掉也没关系*/
            myCamera.stopPreview();
            isPreview = false;
            myCamera.release();
            myCamera = null;
        }

    }

    //初始化相机
    public void initCamera(){
        if(isPreview){
            myCamera.stopPreview();
        }
        if(null != myCamera){
            Camera.Parameters myParam = myCamera.getParameters();
            //          //查询屏幕的宽和高
            //          WindowManager wm = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
            //          Display display = wm.getDefaultDisplay();
            //          Log.i(tag, "屏幕宽度："+display.getWidth()+" 屏幕高度:"+display.getHeight());

            myParam.setPictureFormat(PixelFormat.JPEG);//设置拍照后存储的图片格式

            //          //查询camera支持的picturesize和previewsize
            //          List<Size> pictureSizes = myParam.getSupportedPictureSizes();
            //          List<Size> previewSizes = myParam.getSupportedPreviewSizes();
            //          for(int i=0; i<pictureSizes.size(); i++){
            //              Size size = pictureSizes.get(i);
            //              Log.i(tag, "initCamera:摄像头支持的pictureSizes: width = "+size.width+"height = "+size.height);
            //          }
            //          for(int i=0; i<previewSizes.size(); i++){
            //              Size size = previewSizes.get(i);
            //              Log.i(tag, "initCamera:摄像头支持的previewSizes: width = "+size.width+"height = "+size.height);
            //
            //          }


            //设置大小和方向等参数
            //WindowManager wm = this.getWindowManager();
            //SurfaceView sv=(SurfaceView)view.findViewById(R.id.previewSV);
            if(mPreviewSV!=null)
            {
                int width = mPreviewSV.getWidth();
                int height = mPreviewSV.getHeight();
                Camera.Size size=getOptimalPreviewSize(myCamera.getParameters().getSupportedPreviewSizes(),height,width);
                myParam.setPreviewSize(size.width,size.height);
                //myParam.set("rotation", 90);
                myCamera.setDisplayOrientation(90);
                myParam.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                myCamera.setParameters(myParam);
                myCamera.startPreview();
                myCamera.autoFocus(myAutoFocusCallback);
                myCamera.cancelAutoFocus();
                isPreview = true;
            }
            else
            {
                Toast.makeText(this.getActivity(),"相机初始化失败",Toast.LENGTH_SHORT).show();
            }

        }
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes,int w,int h){
        final double ASP_TOLERANCE=0.1;
        double targetRatio=(double)w/h;
        if(sizes==null)return null;
        Camera.Size optimalSize=null;
        double minDiff=Double.MAX_VALUE;
        int targetHeight=h;
        //Try to find an size match aspect ratio and size
        for(Camera.Size size:sizes){
            double ratio=(double)size.width/size.height;
            if(Math.abs(ratio-targetRatio)>ASP_TOLERANCE)continue;
            if(Math.abs(ratio-targetRatio)<minDiff){
                optimalSize=size;
                minDiff=Math.abs(size.height-targetHeight);
            }
        }

        //Cannot find the one match the aspect ratio,ignore the requirement
        if(optimalSize==null){
            minDiff=Double.MAX_VALUE;
            for(Camera.Size size:sizes) {
                if(Math.abs(size.height-targetHeight)<minDiff){
                    optimalSize=size;
                    minDiff=Math.abs(size.height-targetHeight);
                }
            }
        }
        return optimalSize;
    }

    // 初始化view
    private void initResources() {
        mViewGuide = view.findViewById(R.id.view_guide);
        mViewGuide.setVisibility(View.VISIBLE);
        invisiableHandler.sendMessageDelayed(new Message(), 3000);
        mGuideAnimation = (ImageView) view.findViewById(R.id.guide_animation);
        mDirection = 0.0f;// 初始化起始方向
        mTargetDirection = 0.0f;// 初始化目标方向
        rDirection = 0.0f;// 初始化起始方向
        rTargetDirection = 0.0f;// 初始化目标方向
        mInterpolator = new AccelerateInterpolator();// 实例化加速动画对象
        mStopDrawing = true;
        rStopDrawing=true;
        mChinease = TextUtils.equals(Locale.getDefault().getLanguage(), "zh");// 判断系统当前使用的语言是否为中文
        mCompassView = view.findViewById(R.id.view_compass);// 实际上是一个LinearLayout，装指南针ImageView和位置TextView
        mPointer = (CompassView) view.findViewById(R.id.compass_pointer);// 自定义的指南针view
        rPointer=(CompassView)view.findViewById(R.id.compassbackdround);
        // mLocationTextView = (TextView)
        // findViewById(R.id.textview_location);// 显示位置信息的TextView
        mDirectionLayout = (LinearLayout) view.findViewById(R.id.layout_direction);// 顶部显示方向名称（东南西北）的LinearLayout
        mAngleLayout = (LinearLayout) view.findViewById(R.id.layout_angle);// 顶部显示方向具体度数的LinearLayout

        // mPointer.setImageResource(mChinease ? R.drawable.compass_cn
        // : R.drawable.compass);// 如果系统使用中文，就用中文的指南针图片
    }
    // 初始化传感器和位置服务
    private void initServices() {
        // sensor manager
        mSensorManager = (SensorManager) this.getActivity().getSystemService(Context.SENSOR_SERVICE);
        mOrientationSensor = mSensorManager.getSensorList(
                Sensor.TYPE_ORIENTATION).get(0);//
        // Log.i("way", mOrientationSensor.getName());

        // location manager
        // mLocationManager = (LocationManager)
        // getSystemService(Context.LOCATION_SERVICE);
        // Criteria criteria = new Criteria();// 条件对象，即指定条件过滤获得LocationProvider
        // criteria.setAccuracy(Criteria.ACCURACY_FINE);// 较高精度
        // criteria.setAltitudeRequired(false);// 是否需要高度信息
        // criteria.setBearingRequired(false);// 是否需要方向信息
        // criteria.setCostAllowed(true);// 是否产生费用
        // criteria.setPowerRequirement(Criteria.POWER_LOW);// 设置低电耗
        // mLocationProvider = mLocationManager.getBestProvider(criteria,
        // true);// 获取条件最好的Provider

    }

    // 更新顶部方向显示的方法
    private void updateDirection() {
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        // 先移除layout中所有的view
        mDirectionLayout.removeAllViews();
        mAngleLayout.removeAllViews();

        // 下面是根据mTargetDirection，作方向名称图片的处理
        ImageView east = null;
        ImageView west = null;
        ImageView south = null;
        ImageView north = null;
        float direction = normalizeDegree(mTargetDirection * -1.0f);
        if (direction > 22.5f && direction < 157.5f) {
            // east
            east = new ImageView(view.getContext());
            east.setImageResource(mChinease ? R.drawable.e_cn : R.drawable.e);
            east.setLayoutParams(lp);
        } else if (direction > 202.5f && direction < 337.5f) {
            // west
            west = new ImageView(view.getContext());
            west.setImageResource(mChinease ? R.drawable.w_cn : R.drawable.w);
            west.setLayoutParams(lp);
        }

        if (direction > 112.5f && direction < 247.5f) {
            // south
            south = new ImageView(view.getContext());
            south.setImageResource(mChinease ? R.drawable.s_cn : R.drawable.s);
            south.setLayoutParams(lp);
        } else if (direction < 67.5 || direction > 292.5f) {
            // north
            north = new ImageView(view.getContext());
            north.setImageResource(mChinease ? R.drawable.n_cn : R.drawable.n);
            north.setLayoutParams(lp);
        }
        // 下面是根据系统使用语言，更换对应的语言图片资源
        if (mChinease) {
            // east/west should be before north/south
            if (east != null) {
                mDirectionLayout.addView(east);
            }
            if (west != null) {
                mDirectionLayout.addView(west);
            }
            if (south != null) {
                mDirectionLayout.addView(south);
            }
            if (north != null) {
                mDirectionLayout.addView(north);
            }
        } else {
            // north/south should be before east/west
            if (south != null) {
                mDirectionLayout.addView(south);
            }
            if (north != null) {
                mDirectionLayout.addView(north);
            }
            if (east != null) {
                mDirectionLayout.addView(east);
            }
            if (west != null) {
                mDirectionLayout.addView(west);
            }
        }
        // 下面是根据方向度数显示度数图片数字
        int direction2 = (int) direction;
        boolean show = false;
        if (direction2 >= 100) {
            mAngleLayout.addView(getNumberImage(direction2 / 100));
            direction2 %= 100;
            show = true;
        }
        if (direction2 >= 10 || show) {
            mAngleLayout.addView(getNumberImage(direction2 / 10));
            direction2 %= 10;
        }
        mAngleLayout.addView(getNumberImage(direction2));
        // 下面是增加一个°的图片
        ImageView degreeImageView = new ImageView(view.getContext());
        degreeImageView.setImageResource(R.drawable.degree);
        degreeImageView.setLayoutParams(lp);
        mAngleLayout.addView(degreeImageView);
    }

    // 获取方向度数对应的图片，返回ImageView
    private ImageView getNumberImage(int number) {
        ImageView image = new ImageView(view.getContext());
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        switch (number) {
            case 0:
                image.setImageResource(R.drawable.number_0);
                break;
            case 1:
                image.setImageResource(R.drawable.number_1);
                break;
            case 2:
                image.setImageResource(R.drawable.number_2);
                break;
            case 3:
                image.setImageResource(R.drawable.number_3);
                break;
            case 4:
                image.setImageResource(R.drawable.number_4);
                break;
            case 5:
                image.setImageResource(R.drawable.number_5);
                break;
            case 6:
                image.setImageResource(R.drawable.number_6);
                break;
            case 7:
                image.setImageResource(R.drawable.number_7);
                break;
            case 8:
                image.setImageResource(R.drawable.number_8);
                break;
            case 9:
                image.setImageResource(R.drawable.number_9);
                break;
        }
        image.setLayoutParams(lp);
        return image;
    }


    /*
    // 更新位置显示
    private void updateLocation(Location location) {
        if (location == null) {
            // mLocationTextView.setText(R.string.getting_location);
            return;
        } else {
            // StringBuilder sb = new StringBuilder();
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            String latitudeStr;
            String longitudeStr;
            if (latitude >= 0.0f) {
                latitudeStr = getString(R.string.location_north,
                        getLocationString(latitude));
            } else {
                latitudeStr = getString(R.string.location_south,
                        getLocationString(-1.0 * latitude));
            }

            // sb.append("    ");

            if (longitude >= 0.0f) {
                longitudeStr = getString(R.string.location_east,
                        getLocationString(longitude));
            } else {
                longitudeStr = getString(R.string.location_west,
                        getLocationString(-1.0 * longitude));
            }
            mLatitudeTV.setText(latitudeStr);
            mLongitudeTV.setText(longitudeStr);
            // mLocationTextView.setText(sb.toString());//
            // 显示经纬度，其实还可以作反向编译，显示具体地址
        }
    }

    // 把经纬度转换成度分秒显示
    private String getLocationString(double input) {
        int du = (int) input;
        int fen = (((int) ((input - du) * 3600))) / 60;
        int miao = (((int) ((input - du) * 3600))) % 60;
        return String.valueOf(du) + "°" + String.valueOf(fen) + "′"
                + String.valueOf(miao) + "″";
    }
*/
    // 方向传感器变化监听
    private SensorEventListener mOrientationSensorEventListener = new SensorEventListener() {

        @Override
        public void onSensorChanged(SensorEvent event) {
            float direction = event.values[mSensorManager.DATA_X] * -1.0f;
            if(Nextstep!=null)
            {
                LatLng nodeLocation =null;
                LatLng mLocation=null;
                if (Nextstep instanceof WalkingRouteLine.WalkingStep) {
                    nodeLocation = ((WalkingRouteLine.WalkingStep) Nextstep).getEntrance().getLocation();
                    if(((MainActivity)getActivity()).mBaiduMap!=null)
                    {
                        mLocation=new LatLng(((MainActivity)getActivity()).mBaiduMap.getLocationData().latitude,((MainActivity)getActivity()).mBaiduMap.getLocationData().longitude);
                    }
                    if(nodeLocation!=null&&mLocation!=null)
                    {
                        double rdirection=Math.atan((nodeLocation.latitude-mLocation.latitude)/(nodeLocation.longitude-mLocation.longitude));
                        double exrTarget=(180*rdirection/Math.PI)-270.0f;
                        rTargetDirection=normalizeDegree((((float)exrTarget)-direction)*-1.0f);
                        //double x=((WalkingRouteLine.WalkingStep) Nextstep).getDirection();
                        //rTargetDirection=normalizeDegree((((WalkingRouteLine.WalkingStep) Nextstep).getDirection()-direction)*-1.0f);
                    }
                }
            }
            mUDDirection=normalizeDegree(event.values[1]);//仰角
            mLRDirection=normalizeDegree(event.values[2]);//左右翘起角
            mTargetDirection = normalizeDegree(direction);// 赋值给全局变量，让指南针旋转
            // Log.i("way", event.values[mSensorManager.DATA_Y] + "");
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    // 调整方向传感器获取的值
    private float normalizeDegree(float degree) {
        return (degree + 720) % 360;
    }

    @Override
    public void onPause() {// 在暂停的生命周期里注销传感器服务和位置更新服务

        super.onPause();
        mOverlayHandler.removeCallbacks(mOverlayUpdater);
        if (mOrientationSensor != null) {
            mSensorManager.unregisterListener(mOrientationSensorEventListener);
        }
    }

    @Override
    public void onResume() {// 在恢复的生命周期里判断、启动位置更新服务和传感器服务

        super.onResume();
        // activity 恢复时同时恢复地图控件
        if (mOrientationSensor != null) {
            mSensorManager.registerListener(mOrientationSensorEventListener,
                    mOrientationSensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            // Toast.makeText(this, R.string.cannot_get_sensor,
            // Toast.LENGTH_SHORT)
            // .show();
        }
        mStopDrawing=false;
        if(isGuiding)
        {
            rStopDrawing=false;
        }
        rHandler.postDelayed(rCompassViewUpdater,20);
        mHandler.postDelayed(mCompassViewUpdater,20);
        mOverlayHandler.postDelayed(mOverlayUpdater, 20);
    }

}
