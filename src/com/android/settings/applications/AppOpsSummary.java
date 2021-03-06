/**
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.android.settings.applications;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.openthos.privacyman.AppListFragment;
import org.openthos.privacyman.R;
import org.openthos.privacyman.util.ObjectWrapper;

public class AppOpsSummary extends Fragment {
    // layout inflater object used to inflate views
    private LayoutInflater mInflater;

    private ViewGroup mContentContainer;
    private View mRootView;

    CharSequence[] mPageNames;

    private ListView mAppList;
    private PackageManager mPm;
    private MyAppAdapter mMyAppAdapter;
    private AppOpsDetails mOpsDetails;
    private View mEmptyView;
    private String [] mApps = new String[] {
            "com.google.android.inputmethod.pinyin",
            "com.google.android.tts"};

    class MyAppAdapter extends BaseAdapter {

        private List<ApplicationInfo> mList;
        private ViewHolder holder;
        private int mPosition = 0;

        public MyAppAdapter() {
        }

        public void setData(List<ApplicationInfo> list) {
            mList = list;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mList != null ? mList.size() : 0;
        }

        @Override
        public Object getItem(int i) {
            return mList != null ? mList.get(i) : null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if (view == null) {
                view = mInflater.inflate(R.layout.app_item, viewGroup, false);
                holder = new ViewHolder(view);
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }

            ApplicationInfo info = mList.get(i);
            holder.appName.setText(info.loadLabel(mPm));
            holder.icon.setImageDrawable(info.loadIcon(mPm));
            holder.appLayout.setBackgroundColor(Color.TRANSPARENT);
            if (i == mPosition) {
                holder.appLayout.setBackgroundColor(0x80808080);
            }

            return view;
        }

        public void setSelectItem(int position) {
            this.mPosition = position;
        }

        public class ViewHolder {

            private final TextView appName;
            private final ImageView icon;
            private final LinearLayout appLayout;

            public ViewHolder(View view) {
                appName = (TextView) view.findViewById(R.id.app_name);
                icon = (ImageView) view.findViewById(R.id.app_icon);
                appLayout = (LinearLayout) view.findViewById(R.id.applist_layout);
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        super.onCreate(savedInstanceState);
        mPm = getActivity().getPackageManager();
        mMyAppAdapter = new MyAppAdapter();
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                        ViewGroup container, Bundle savedInstanceState) {
        // initialize the inflater
        mInflater = inflater;

        View rootView = mInflater.inflate(R.layout.app_ops_summary,
                container, false);
        mContentContainer = container;
        mRootView = rootView;

        mPageNames = getResources().getTextArray(R.array.app_ops_categories);

        mAppList = (ListView) rootView.findViewById(R.id.classify_applist);
        mEmptyView = rootView.findViewById(R.id.empty);

        List<ApplicationInfo> installedApp = getInstalledApp();
        if (installedApp.size() == 0) {
            mAppList.setEmptyView(mEmptyView);
        } else {
            initData(installedApp);
        }

        if (container != null && "android.preference.PreferenceFrameLayout"
                                                .equals(container.getClass().getName())) {
            new ObjectWrapper(rootView.getLayoutParams()).set("removeBorders", true);
        }

        registerAppReceiver();

        return rootView;
    }

    private void initData(List<ApplicationInfo> installedApp) {
        mMyAppAdapter.setData(installedApp);
        mMyAppAdapter.setSelectItem(mMyAppAdapter.mPosition);
        mAppList.setAdapter(mMyAppAdapter);

        final FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        mOpsDetails = new AppOpsDetails();
        mOpsDetails.setPkgName(((ApplicationInfo)mAppList.
                getItemAtPosition(mMyAppAdapter.mPosition)).packageName);
        transaction.replace(R.id.fragment_content, mOpsDetails).commit();

        mAppList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                mMyAppAdapter.setSelectItem(i);
                ApplicationInfo info = (ApplicationInfo) mAppList.getItemAtPosition(i);
                mOpsDetails.setPkgName(info.packageName);
                mOpsDetails.onResume();
                mMyAppAdapter.notifyDataSetChanged();
            }
        });
    }

    private void registerAppReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        getActivity().registerReceiver(mAppReceiver, filter);
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
//        menu.add(R.string.show_changed_only_title)
//                                .setOnMenuItemClickListener(new OnMenuItemClickListener() {
//
//            @Override
//            public boolean onMenuItemClick(MenuItem item) {
//                ((PreferenceActivity) getActivity())
//                            .startPreferenceFragment(AppListFragment.newInstance(true), true);
//                return true;
//            }
//        });
//        menu.add(R.string.settings).setOnMenuItemClickListener(new OnMenuItemClickListener() {
//
//            @Override
//            public boolean onMenuItemClick(MenuItem item) {
//                getActivity().startActivity(new Intent(getActivity(), SettingsActivity.class));
//                return true;
//            }
//        });
        //inflater.inflate(R.menu.changed_list, menu);
        super.onCreateOptionsMenu(menu,inflater);
        MenuItem item = menu.add(0, 0, 0, "show changed list");
        item.setTitle(R.string.show_changed_only_title);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        ((PreferenceActivity) getActivity())
                            .startPreferenceFragment(AppListFragment.newInstance(true), true);
        return true;
    }

    private List<ApplicationInfo> getInstalledApp() {
        List<ApplicationInfo> lists = new ArrayList<>();
        List<ApplicationInfo> installedApplications = mPm.getInstalledApplications(0);
        for (ApplicationInfo info : installedApplications) {
            if (info.sourceDir.indexOf("data/app") == -1) continue;
            if (Arrays.asList(mApps).contains(info.packageName)) continue;
            lists.add(info);
        }
        return lists;
    }

    private BroadcastReceiver mAppReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case Intent.ACTION_PACKAGE_ADDED:
                    List<ApplicationInfo> addInfos = getInstalledApp();
                    if (addInfos.size() == 1) {
                        initData(addInfos);
                    } else {
                        refreshUI(addInfos);
                    }
                    break;
                case Intent.ACTION_PACKAGE_REMOVED:
                case Intent.ACTION_PACKAGE_REPLACED:
                    List<ApplicationInfo> removedInfos = getInstalledApp();
                    if (removedInfos.size() == 0) {
                        mEmptyView.setVisibility(View.VISIBLE);
                    } else {
                        refreshUI(removedInfos);
                    }
                    break;
            }
        }
    };

    private void refreshUI(List<ApplicationInfo> infos) {
        mEmptyView.setVisibility(View.GONE);
        mMyAppAdapter.setData(infos);
        int selectItem = mMyAppAdapter.mPosition < infos.size()
                                ? mMyAppAdapter.mPosition : infos.size() - 1;
        ApplicationInfo item = (ApplicationInfo) mMyAppAdapter.getItem(selectItem);
        mOpsDetails.setPkgName(item.packageName);
        mOpsDetails.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().unregisterReceiver(mAppReceiver);
    }
}
