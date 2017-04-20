package com.example.test.app;

import android.os.Bundle;
import android.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.baidu.mapapi.map.*;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.overlayutil.PoiOverlay;
import com.baidu.mapapi.search.core.CityInfo;
import com.baidu.mapapi.search.core.PoiInfo;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.*;
import com.baidu.mapapi.search.poi.*;
import com.baidu.mapapi.search.sug.OnGetSuggestionResultListener;
import com.baidu.mapapi.search.sug.SuggestionResult;
import com.baidu.mapapi.search.sug.SuggestionSearch;
import com.baidu.mapapi.search.sug.SuggestionSearchOption;


/**
 * Created by GGBomb2 on 2015/11/8.
 */
public class GuideFragment extends Fragment implements OnGetPoiSearchResultListener, OnGetSuggestionResultListener,
        OnGetGeoCoderResultListener {
    View view=null;
    //浏览路线节点相关
    Button mBtnPre = null;//上一个节点
    Button mBtnNext = null;//下一个节点
    Button btn_Walksearch=null;
    Button btn_Drivesearch=null;
    Button btn_Transitsearch=null;
    RelativeLayout.LayoutParams mlayoutParams=null;
    private BaiduMap mBaiduMap = null;
    private PoiSearch mPoiSearch = null;//关键词搜索
    private SuggestionSearch mSuggestionSearch = null;//搜索建议模块
    /**
     * 搜索关键字输入窗口
     */
    GeoCoder mSearch = null; // 搜索模块，也可去掉地图模块独立使用
    String mCity=null;
    private AutoCompleteTextView keyWorldsView = null;
    private ArrayAdapter<String> sugAdapter = null;
    private int load_Index = 0;
    LatLng mDestination=null;



    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        view= inflater.inflate(R.layout.guide,container,false);
        mBaiduMap=((MainActivity)getActivity()).mBaiduMap;
        // 初始化搜索模块，注册事件监听
        mSearch = GeoCoder.newInstance();
        mSearch.setOnGetGeoCodeResultListener(this);
        mSearch.reverseGeoCode(new ReverseGeoCodeOption().location(new LatLng(mBaiduMap.getLocationData().latitude,mBaiduMap.getLocationData().longitude)));//发起搜索
        RelativeLayout rly=(RelativeLayout)getActivity().findViewById(R.id.relativeLayout);
        mlayoutParams=(RelativeLayout.LayoutParams)rly.getLayoutParams();
        RelativeLayout rly2=(RelativeLayout)view.findViewById(R.id.relativeLayout_test);
        RelativeLayout.LayoutParams layoutParams=(RelativeLayout.LayoutParams)rly2.getLayoutParams();
        rly.setLayoutParams(layoutParams);
        mBtnPre = (Button) view.findViewById(R.id.pre);
        mBtnNext = (Button) view.findViewById(R.id.next);
        btn_Walksearch=(Button)view.findViewById(R.id.walk);
        btn_Drivesearch=(Button)view.findViewById(R.id.drive);
        btn_Transitsearch=(Button)view.findViewById(R.id.transit);
        mBtnPre.setVisibility(View.INVISIBLE);
        mBtnNext.setVisibility(View.INVISIBLE);
        LinearLayout llayout=(LinearLayout)view.findViewById(R.id.linearlayout_search);
        llayout.setVisibility(View.INVISIBLE);
        // 初始化搜索模块，注册搜索事件监听
        mPoiSearch = PoiSearch.newInstance();
        mPoiSearch.setOnGetPoiSearchResultListener(this);
        mSuggestionSearch = SuggestionSearch.newInstance();
        mSuggestionSearch.setOnGetSuggestionResultListener(this);
        keyWorldsView = (AutoCompleteTextView) view.findViewById(R.id.end);
        sugAdapter = new ArrayAdapter<String>(getActivity(),
                android.R.layout.simple_dropdown_item_1line);
        keyWorldsView.setAdapter(sugAdapter);
        Log.i(getTag(),"GuideFragment onCreated!");
        //按键消息监听

        mBtnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity)getActivity()).nodeClick(v);
            }
        });
        mBtnPre.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity)getActivity()).nodeClick(v);
            }
        });
        btn_Walksearch=(Button)view.findViewById(R.id.walk);
        btn_Walksearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity)getActivity()).SearchButtonProcess(v,mDestination);
            }
        });
        btn_Drivesearch=(Button)view.findViewById(R.id.drive);
        btn_Drivesearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBtnPre.setVisibility(View.INVISIBLE);
                mBtnNext.setVisibility(View.INVISIBLE);
                ((MainActivity)getActivity()).SearchButtonProcess(v,mDestination);
            }
        });
        btn_Transitsearch=(Button)view.findViewById(R.id.transit);
        btn_Transitsearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBtnPre.setVisibility(View.INVISIBLE);
                mBtnNext.setVisibility(View.INVISIBLE);
                ((MainActivity)getActivity()).SearchButtonProcess(v,mDestination);
            }
        });
        Button btn_Go=(Button)view.findViewById(R.id.btn_go);
        btn_Go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LinearLayout llayout=(LinearLayout)view.findViewById(R.id.linearlayout_search);
                llayout.setVisibility(View.VISIBLE);
                mBaiduMap.setMyLocationConfigeration(new MyLocationConfiguration(MyLocationConfiguration.LocationMode.NORMAL, true, null));
               searchButtonProcess(v);
            }
        });

        /**
         * 当输入关键字变化时，动态更新建议列表
         */
        keyWorldsView.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable arg0) {
                LinearLayout llayout=(LinearLayout)view.findViewById(R.id.linearlayout_search);
                llayout.setVisibility(View.INVISIBLE);
            }

            @Override
            public void beforeTextChanged(CharSequence arg0, int arg1,
                                          int arg2, int arg3) {
            }

            @Override
            public void onTextChanged(CharSequence cs, int arg1, int arg2,
                                      int arg3) {
                if (cs.length() <= 0) {
                    return;
                }
                String city = mCity;
                //((EditText) findViewById(R.id.city)).getText().toString();
                /**
                 * 使用建议搜索服务获取建议列表，结果在onSuggestionResult()中更新
                 */
                mSuggestionSearch
                        .requestSuggestion((new SuggestionSearchOption())
                                .keyword(cs.toString()).city(city));
            }
        });

        return view;
    }





    @Override
    public void onPause() {
        Log.i(getTag(),"GuideFragment onPaused!");
        super.onPause();
    }

    @Override
    public void onResume() {
        Log.i(getTag(),"GuideFragment onResumed!");
        super.onResume();
    }

    @Override
    public void onDestroy() {
        Log.i(getTag(),"GuideFragment onDestroyed!");
        ((MainActivity)getActivity()).mGuideFragment=null;
        RelativeLayout rly=(RelativeLayout)getActivity().findViewById(R.id.relativeLayout);
        rly.setLayoutParams(mlayoutParams);
        mPoiSearch.destroy();
        mSuggestionSearch.destroy();
        mSearch.destroy();
        mBaiduMap.setMyLocationConfigeration(new MyLocationConfiguration(MyLocationConfiguration.LocationMode.COMPASS, true, null));
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    /**
     * 影响搜索按钮点击事件
     *
     * @param v
     */
    public void searchButtonProcess(View v) {
        //EditText editCity = (EditText) view.findViewById(R.id.city);
        EditText editSearchKey = (EditText) view.findViewById(R.id.end);
        mPoiSearch.searchInCity((new PoiCitySearchOption())
                .city(mCity)
                .keyword(editSearchKey.getText().toString())
                .pageNum(load_Index));
    }

    public void goToNextPage(View v) {
        load_Index++;
        searchButtonProcess(null);
    }

    public void onGetPoiResult(PoiResult result) {
        if (result == null
                || result.error == SearchResult.ERRORNO.RESULT_NOT_FOUND) {
            Toast.makeText(getActivity(), "未找到结果", Toast.LENGTH_LONG)
                    .show();
            return;
        }
        if (result.error == SearchResult.ERRORNO.NO_ERROR) {
            mBaiduMap.clear();
            PoiOverlay overlay = new MyPoiOverlay(mBaiduMap);
            mBaiduMap.setOnMarkerClickListener(overlay);
            overlay.setData(result);
            overlay.addToMap();
            //overlay.zoomToSpan();
            MapStatusUpdate u=MapStatusUpdateFactory.newLatLng(result.getAllPoi().get(0).location);
            mBaiduMap.animateMapStatus(u);
            mDestination=result.getAllPoi().get(0).location;
            int count=result.getAllPoi().size();
            PoiInfo[] poiinfos=new PoiInfo[count];
            for(int n=0;n<count;n++)
            {
                poiinfos[n]=result.getAllPoi().get(n);
            }
            if(((MainActivity)getActivity()).mSurfaceFragment!=null)
            {
                ((MainActivity)getActivity()).mSurfaceFragment.poiinfos=poiinfos;
            }

            return;
        }
        if (result.error == SearchResult.ERRORNO.AMBIGUOUS_KEYWORD) {

            // 当输入关键字在本市没有找到，但在其他城市找到时，返回包含该关键字信息的城市列表
            String strInfo = "在";
            for (CityInfo cityInfo : result.getSuggestCityList()) {
                strInfo += cityInfo.city;
                strInfo += ",";
            }
            strInfo += "找到结果";
            Toast.makeText(getActivity(), strInfo, Toast.LENGTH_LONG)
                    .show();
        }
    }

    public void onGetPoiDetailResult(PoiDetailResult result) {
        if (result.error != SearchResult.ERRORNO.NO_ERROR) {
            Toast.makeText(getActivity(), "抱歉，未找到结果", Toast.LENGTH_SHORT)
                    .show();
        } else {
            Toast.makeText(getActivity(), result.getName() + ": " + result.getAddress(), Toast.LENGTH_SHORT)
                    .show();
        }
    }

    @Override
    public void onGetSuggestionResult(SuggestionResult res) {
        if (res == null || res.getAllSuggestions() == null) {
            return;
        }
        sugAdapter.clear();
        for (SuggestionResult.SuggestionInfo info : res.getAllSuggestions()) {
            if (info.key != null)
                sugAdapter.add(info.key);
        }
        sugAdapter.notifyDataSetChanged();
    }

    private class MyPoiOverlay extends PoiOverlay {

        public MyPoiOverlay(BaiduMap baiduMap) {
            super(baiduMap);
        }

        @Override
        public boolean onPoiClick(int index) {
            super.onPoiClick(index);
            PoiInfo poi = getPoiResult().getAllPoi().get(index);
            // if (poi.hasCaterDetails) {
            mPoiSearch.searchPoiDetail((new PoiDetailSearchOption())
                    .poiUid(poi.uid));
            MapStatusUpdate u=MapStatusUpdateFactory.newLatLng(poi.location);
            mBaiduMap.animateMapStatus(u);
            mDestination=poi.location;
            // }
            return true;
        }
    }
    @Override
    public void onGetGeoCodeResult(GeoCodeResult result) {

    }
    @Override
    public void onGetReverseGeoCodeResult(ReverseGeoCodeResult result) {
        if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
            Toast.makeText(getActivity(), "抱歉，未能找到你所在的城市", Toast.LENGTH_LONG)
                    .show();
            return;
        }
        /*
        mBaiduMap.clear();
        mBaiduMap.addOverlay(new MarkerOptions().position(result.getLocation())
                .icon(BitmapDescriptorFactory
                        .fromResource(R.drawable.icon_marka)));
        mBaiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(result
                .getLocation()));
        */
        mCity=result.getAddressDetail().city;
        Toast.makeText(getActivity(), result.getAddress(),
                Toast.LENGTH_LONG).show();

    }

}
