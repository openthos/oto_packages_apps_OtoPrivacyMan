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
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import at.jclehner.appopsxposed.AppListFragment;
import at.jclehner.appopsxposed.R;
import at.jclehner.appopsxposed.SettingsActivity;
import at.jclehner.appopsxposed.util.ObjectWrapper;

public class AppOpsSummary extends Fragment {
    // layout inflater object used to inflate views
    private LayoutInflater mInflater;

    private ViewGroup mContentContainer;
    private View mRootView;

    CharSequence[] mPageNames;
    static AppOpsState.OpsTemplate[] sPageTemplates = new AppOpsState.OpsTemplate[] {
        AppOpsState.LOCATION_TEMPLATE,
        AppOpsState.PERSONAL_TEMPLATE,
        AppOpsState.MESSAGING_TEMPLATE,
        AppOpsState.MEDIA_TEMPLATE,
        AppOpsState.DEVICE_TEMPLATE,
        AppOpsState.BOOTUP_TEMPLATE
    };

    private ListView mAppList;
    private PackageManager mPm;

    class MyAppAdapter extends BaseAdapter {

        private List<ApplicationInfo> mList;
        private ViewHolder holder;
        private int mPosition = 0;

        public MyAppAdapter(List<ApplicationInfo> list) {
            mList = list;
        }

        @Override
        public int getCount() {
            return mList != null ? mList.size() : 0;
        }

        @Override
        public Object getItem(int i) {
            return mList.get(i);
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
        final MyAppAdapter myAppAdapter = new MyAppAdapter(getInstalledApp());
        myAppAdapter.setSelectItem(0);
        mAppList.setAdapter(myAppAdapter);

        final FragmentTransaction transaction = getActivity()
                                    .getFragmentManager().beginTransaction();
        final AppOpsDetails opsDetails = new AppOpsDetails();
        opsDetails.setPkgName(((ApplicationInfo)mAppList.getItemAtPosition(0)).packageName);
        mAppList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                myAppAdapter.setSelectItem(i);
                ApplicationInfo info = (ApplicationInfo) mAppList.getItemAtPosition(i);
                opsDetails.setPkgName(info.packageName);
                opsDetails.onResume();
                myAppAdapter.notifyDataSetChanged();
            }
        });
        transaction.replace(R.id.fragment_content, opsDetails).commit();

        if (container != null && "android.preference.PreferenceFrameLayout"
                                                .equals(container.getClass().getName())) {
            new ObjectWrapper(rootView.getLayoutParams()).set("removeBorders", true);
        }

        return rootView;
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(R.string.show_changed_only_title)
                                .setOnMenuItemClickListener(new OnMenuItemClickListener() {

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                ((PreferenceActivity) getActivity())
                            .startPreferenceFragment(AppListFragment.newInstance(true), true);
                return true;
            }
        });
        menu.add(R.string.settings).setOnMenuItemClickListener(new OnMenuItemClickListener() {

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                getActivity().startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
        });
    }

    private List<ApplicationInfo> getInstalledApp() {
        List<ApplicationInfo> lists = new ArrayList<>();
        List<ApplicationInfo> installedApplications = mPm.getInstalledApplications(0);
        for (ApplicationInfo info : installedApplications) {
            if (info.sourceDir.indexOf("data/app") == -1) continue;
            lists.add(info);
        }
        return lists;
    }

}
