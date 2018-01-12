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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import at.jclehner.appopsxposed.R;
import at.jclehner.appopsxposed.util.AppOpsManagerWrapper;
import at.jclehner.appopsxposed.util.AppOpsManagerWrapper.OpEntryWrapper;
import at.jclehner.appopsxposed.util.AppOpsManagerWrapper.PackageOpsWrapper;

public class AppOpsDetails extends Fragment {
    static final String TAG = "AppOpsDetails";

    public static final String ARG_PACKAGE_NAME = "package";
    public static final String GROUP_ITEM = "group";
    public static final String CHECKED = "checked";
    public static final int GROUPSW_MSG = 1;
    public static final int ITEMSW_MSG = 0;

    private AppOpsState mState;
    private PackageManager mPm;
    private AppOpsManagerWrapper mAppOps;
    private PackageInfo mPackageInfo;
    private LayoutInflater mInflater;
    private View mRootView;
    private TextView mAppVersion;
    private String mPackageName;
    private SharedPreferences mSp;
    private CharSequence[] mTypeNames;

    private final int VIEW_TYPE_COUNT = 2;

    private final String DATA = "data";
    private final String TYPE = "type";

    private final int GROUP = -2;
    private final int ITEM = -3;

    private ArrayList<HashMap<String, Object>> mItems = null;
    private ListView mOperationsList;
    private AppOpsAdapter mOpsAdapter;

    // Utility method to set application label and icon.
    private void setAppLabelAndIcon(PackageInfo pkgInfo) {
        final View appSnippet = mRootView.findViewById(R.id.app_snippet);
        appSnippet.setPaddingRelative(0, appSnippet.getPaddingTop(), 0, appSnippet.getPaddingBottom());

        ImageView icon = (ImageView) appSnippet.findViewById(R.id.app_icon);
        icon.setImageDrawable(mPm.getApplicationIcon(pkgInfo.applicationInfo));
        // Set application name.
        TextView label = (TextView) appSnippet.findViewById(R.id.app_name);
        label.setText(mPm.getApplicationLabel(pkgInfo.applicationInfo));
        // Version number of application
        mAppVersion = (TextView) appSnippet.findViewById(R.id.app_size);

        final StringBuilder sb = new StringBuilder(pkgInfo.packageName);

        if (pkgInfo.versionName != null) {
            sb.append("\n");
            sb.append(getActivity().getString(R.string.version_text, pkgInfo.versionName));
        }

        mAppVersion.setText(sb);
    }

    private String retrieveAppEntry() {
//        final Bundle args = getArguments();
//        String packageName = (args != null) ? args.getString(ARG_PACKAGE_NAME) : null;
//        if (packageName == null) {
//            Intent intent = (args == null) ?
//                    getActivity().getIntent() : (Intent) args.getParcelable("intent");
//            if (intent != null) {
//                packageName = intent.getData().getSchemeSpecificPart();
//            }
//        }
        String packageName = getPkgName();
        try {
            mPackageInfo = mPm.getPackageInfo(packageName,
                    PackageManager.GET_DISABLED_COMPONENTS |
                    PackageManager.GET_UNINSTALLED_PACKAGES);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Exception when retrieving package:" + packageName, e);
            mPackageInfo = null;
        }

        if (getActivity().getPackageName().equals(packageName)) {
            Toast.makeText(getActivity(), "\uD83D\uDE22", Toast.LENGTH_SHORT).show();
        }

        return packageName;
    }

    private boolean refreshUi() {
        if (mPackageInfo == null) {
            return false;
        }

        setAppLabelAndIcon(mPackageInfo);
        mSp = getActivity().getSharedPreferences(mPackageInfo.packageName, Context.MODE_PRIVATE);

        mTypeNames = getActivity().getResources().getTextArray(R.array.app_ops_categories);

        mItems.clear();
        for (int i = 0; i < AppOpsState.ALL_TEMPLATES.length - 1; i++) {
            AppOpsState.OpsTemplate tpl = AppOpsState.ALL_TEMPLATES[i];
            final List<AppOpsState.AppOpEntry> entries = mState.buildState(tpl,
                    mPackageInfo.applicationInfo.uid, mPackageInfo.packageName);
            if (entries.size() > 0) {
                HashMap<String, Object> groupMap = new HashMap<>();
                groupMap.put(TYPE, GROUP);
                groupMap.put(DATA, i);
                mItems.add(groupMap);
            }
            for (final AppOpsState.AppOpEntry entry : entries) {
                HashMap<String, Object> dataMap = new HashMap<>();
                dataMap.put(TYPE, ITEM);
                dataMap.put(DATA, entry);
                mItems.add(dataMap);
            }
        }
        mOpsAdapter.setData(mItems);

        return true;
    }

    private void setAppOpsMode(boolean isChecked, AppOpsState.AppOpEntry entry, int switchOp) {
        mAppOps.setMode(switchOp, entry.getPackageOps().getUid(),
                entry.getPackageOps().getPackageName(), isChecked
                        ? AppOpsManagerWrapper.MODE_ALLOWED : AppOpsManagerWrapper.MODE_IGNORED);
    }

    private Handler mUiHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            List<Boolean> list = new ArrayList<>();
            switch (msg.what) {
                case ITEMSW_MSG:
                    for (int i = 0; i < AppOpsState.ALL_TEMPLATES.length - 1; i++){
                        AppOpsState.OpsTemplate tpl = AppOpsState.ALL_TEMPLATES[i];
                        final List<AppOpsState.AppOpEntry> entries = mState.buildState(tpl,
                                mPackageInfo.applicationInfo.uid, mPackageInfo.packageName);
                        list.clear();
                        for (AppOpsState.AppOpEntry entry : entries) {
                            OpEntryWrapper firstOp = entry.getOpEntry(0);
                            int switchOp = AppOpsManagerWrapper.opToSwitch(firstOp.getOp());
                            list.add(modeToChecked(switchOp, entry.getPackageOps()));
                        }
                        if (entries.size() > 0 && !list.contains(true)) {
                            mSp.edit().putBoolean(mTypeNames[i].toString(), false).commit();
                        } else if (entries.size() > 0 && list.contains(true)) {
                            mSp.edit().putBoolean(mTypeNames[i].toString(), true).commit();
                        }
                    }
                    break;
                case GROUPSW_MSG:
                    Bundle data = msg.getData();
                    int index = (int) data.get(GROUP_ITEM);
                    boolean isChecked = data.getBoolean(CHECKED);
                    AppOpsState.OpsTemplate tpl = AppOpsState.ALL_TEMPLATES[index];
                    final List<AppOpsState.AppOpEntry> entries = mState.buildState(tpl,
                            mPackageInfo.applicationInfo.uid, mPackageInfo.packageName);
                    for (AppOpsState.AppOpEntry entry : entries) {
                        OpEntryWrapper firstOp = entry.getOpEntry(0);
                        int switchOp = AppOpsManagerWrapper.opToSwitch(firstOp.getOp());
                        setAppOpsMode(isChecked, entry, switchOp);
                    }
                    break;
            }
            mOpsAdapter.notifyDataSetChanged();
        }
    };

    private boolean modeToChecked(int switchOp, PackageOpsWrapper ops) {
       return modeToChecked(mAppOps.checkOpNoThrow(switchOp, ops.getUid(), ops.getPackageName()));
    }

    static boolean modeToChecked(int mode) {
        if (mode == AppOpsManagerWrapper.MODE_ALLOWED)
            return true;
        if (mode == AppOpsManagerWrapper.MODE_DEFAULT)
            return true;
        if (mode == AppOpsManagerWrapper.MODE_ASK)
            return true;
        if (mode == AppOpsManagerWrapper.MODE_HINT)
            return true;

        return false;
    }

    private void setIntentAndFinish(boolean finish, boolean appChanged) {
        Intent intent = new Intent();
        intent.putExtra("chg", appChanged);
        PreferenceActivity pa = (PreferenceActivity)getActivity();
        pa.finishPreferencePanel(this, Activity.RESULT_OK, intent);
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mState = new AppOpsState(getActivity());
        mPm = getActivity().getPackageManager();
        mInflater = (LayoutInflater)getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mAppOps = AppOpsManagerWrapper.from(getActivity());

        retrieveAppEntry();

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.app_ops_details, container, false);
        //Utils.prepareCustomPreferencesList(container, view, view, false);

        mRootView = view;
        mOperationsList = (ListView) view.findViewById(R.id.operations_list);

        mItems = new ArrayList<>();
        mOpsAdapter = new AppOpsAdapter();
        mOpsAdapter.setData(mItems);
        mOperationsList.setAdapter(mOpsAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        retrieveAppEntry();
        if (!refreshUi()) {
            setIntentAndFinish(true, true);
        }
    }

    public String getPkgName() {
        return mPackageName;
    }

    public void setPkgName(String packageName) {
        this.mPackageName = packageName;
    }

    class AppOpsAdapter extends BaseAdapter {

        private ArrayList<HashMap<String, Object>> mDatas;
        private String lastPermGroup = "";

        public AppOpsAdapter() {
        }

        public void setData(ArrayList<HashMap<String, Object>> items) {
            this.mDatas = items;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mDatas.size();
        }

        @Override
        public Object getItem(int position) {
            HashMap<String, Object> map = mDatas.get(position);
            return map.get(DATA);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup viewGroup) {
            GroupViewHolder groupViewHolder = null;
            DataViewHolder dataViewHolder = null;

            int type = getItemViewType(position);
            switch (type) {
                case GROUP:
                    if (convertView == null) {
                        convertView = mInflater.inflate(R.layout.app_ops_type_item, null);
                        groupViewHolder = new GroupViewHolder(convertView);
                        convertView.setTag(groupViewHolder);
                    } else {
                        groupViewHolder = (GroupViewHolder) convertView.getTag();
                    }
                    final int item = (Integer) mDatas.get(position).get(DATA);
                    groupViewHolder.mGroupName.setText(mTypeNames[item]);
                    groupViewHolder.mGroupSw.setChecked(
                                        mSp.getBoolean(mTypeNames[item].toString(), true));
                    groupViewHolder.mGroupSw.setOnCheckedChangeListener(
                                        new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton compoundButton,
                                                     boolean isChecked) {
                            mSp.edit().putBoolean(mTypeNames[item].toString(),
                                    isChecked).commit();
                            Message message = new Message();
                            message.what = GROUPSW_MSG;
                            Bundle bundle = new Bundle();
                            bundle.putInt(GROUP_ITEM, item);
                            bundle.putBoolean(CHECKED, isChecked);
                            message.setData(bundle);
                            mUiHandler.sendMessage(message);
                        }
                    });
                    break;
                case ITEM:
                    if (convertView == null) {
                        convertView = mInflater.inflate(R.layout.app_ops_details_item, null);
                        dataViewHolder = new DataViewHolder(convertView);
                        convertView.setTag(dataViewHolder);
                    } else {
                        dataViewHolder = (DataViewHolder) convertView.getTag();
                    }
                    final AppOpsState.AppOpEntry entry =
                                        (AppOpsState.AppOpEntry) getItem(position);
                    OpEntryWrapper firstOp = entry.getOpEntry(0);
                    final int switchOp = AppOpsManagerWrapper.opToSwitch(firstOp.getOp());

                    String perm = AppOpsManagerWrapper.opToPermission(firstOp.getOp());
                    if (perm != null) {
                        try {
                            PermissionInfo pi = mPm.getPermissionInfo(perm, 0);
                            if (pi.group != null && !lastPermGroup.equals(pi.group)) {
                                lastPermGroup = pi.group;
                                PermissionGroupInfo pgi = mPm.getPermissionGroupInfo(pi.group, 0);
                                if (pgi.icon != 0) {
                                    dataViewHolder.mOpIcon.setImageDrawable(
                                            pgi.loadIcon(mPm));
                                }
                            }
                        } catch (NameNotFoundException e) {
                        }
                    }

                    dataViewHolder.mOpName.setText(entry.getSwitchText(getActivity(), mState));
                    dataViewHolder.mOpTime.setText(entry.getTimeText(getResources(), true));

                    dataViewHolder.mOpSw.setChecked(modeToChecked(switchOp,
                                                        entry.getPackageOps()));

                    dataViewHolder.mOpSw.setOnCheckedChangeListener(
                                        new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton compoundButton,
                                                            boolean isChecked) {
                            setAppOpsMode(isChecked, entry, switchOp);
                            mUiHandler.sendEmptyMessage(ITEMSW_MSG);
                        }
                    });

                    break;
            }

            return convertView;
        }

        @Override
        public int getItemViewType(int position) {
            HashMap<String, Object> map = mDatas.get(position);
            return (int) map.get(TYPE);
        }

        @Override
        public int getViewTypeCount() {
            return VIEW_TYPE_COUNT;
        }
    }

    public class GroupViewHolder {

        private final TextView mGroupName;
        private final Switch mGroupSw;

        public GroupViewHolder(View convertView) {
            mGroupName = (TextView) convertView.findViewById(R.id.type_name);
            mGroupSw = (Switch) convertView.findViewById(R.id.switchTypeWidget);
        }
    }

    public class DataViewHolder {

        private final TextView mOpName;
        private final TextView mOpTime;
        private final Switch mOpSw;
        private final ImageView mOpIcon;

        public DataViewHolder(View convertView) {
            mOpName = (TextView) convertView.findViewById(R.id.op_name);
            mOpTime = (TextView) convertView.findViewById(R.id.op_time);
            mOpSw = (Switch) convertView.findViewById(R.id.switchWidget);
            mOpIcon = (ImageView) convertView.findViewById(R.id.op_icon);
        }
    }
}
