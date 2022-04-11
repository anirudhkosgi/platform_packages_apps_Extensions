/*
 * Copyright (C) 2018 AospExtended ROM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.aospextended.extensions.fragments;

import static android.os.UserHandle.USER_SYSTEM;

import android.app.ActivityManagerNative;
import android.app.UiModeManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.IWindowManager;
import android.view.View;
import android.view.WindowManagerGlobal;
import android.widget.Toast;

import androidx.annotation.VisibleForTesting;
import androidx.preference.ListPreference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.aospextended.AEXUtils;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.search.BaseSearchIndexProvider;

import com.android.settingslib.search.Indexable;
import com.android.settingslib.search.SearchIndexable;

import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collection;
import java.util.List;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.aospextended.support.colorpicker.ColorPickerPreference;
import org.aospextended.extensions.fragments.UdfpsIconPicker;

@SearchIndexable
public class Customisation extends SettingsPreferenceFragment implements OnPreferenceChangeListener {

    private static final String TAG = "Customisation";

    private Context mContext;
    
    private static final String CUSTOM_FOD_ICON_KEY = "custom_fp_icon_enabled";
    private static final String CUSTOM_FP_FILE_SELECT = "custom_fp_file_select";
    private static final int REQUEST_PICK_IMAGE = 0;

    private Preference mCustomFPImage;
    private SystemSettingSwitchPreference mCustomFodIcon;
    private Preference mUdfpsIconPicker;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.customisation);

        mContext = getActivity();

        final ContentResolver resolver = getActivity().getContentResolver();
        final PreferenceScreen screen = getPreferenceScreen();

        /**
        boolean udfpsResPkgInstalled = AEXUtils.isPackageInstalled(getContext(),
                "org.aospextended.udfps.resources");
        
        PreferenceCategory udfps = (PreferenceCategory) screen.findPreference("udfps_category");*/
        
        mUdfpsIconPicker = (PreferenceCategory) Screen.findPreference("udfps_icon_picker");

        mCustomFPImage = findPreference(CUSTOM_FP_FILE_SELECT);
        final String customIconURI = Settings.System.getString(getContext().getContentResolver(),
               Settings.System.OMNI_CUSTOM_FP_ICON);
        if (!TextUtils.isEmpty(customIconURI)) {
            setPickerIcon(customIconURI);
        }

        mCustomFodIcon = (SystemSettingSwitchPreference) findPreference(CUSTOM_FOD_ICON_KEY);
        boolean val = Settings.System.getIntForUser(getActivity().getContentResolver(),
                Settings.System.OMNI_CUSTOM_FP_ICON_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
        mCustomFodIcon.setOnPreferenceChangeListener(this);
        if (val) {
            mUdfpsIconPicker.setEnabled(false);
        } else {
            mUdfpsIconPicker.setEnabled(true);
        }        
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.EXTENSIONS;
    }

    @Override
    public void onResume() {
        super.onResume();
    }
    
    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mCustomFPImage) {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_PICK_IMAGE);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mCustomFodIcon) {
            boolean val = (Boolean) newValue;
            Settings.System.putIntForUser(getActivity().getContentResolver(),
                    Settings.System.OMNI_CUSTOM_FP_ICON_ENABLED, val ? 1 : 0,
                    UserHandle.USER_CURRENT);
            if (val) {
                mUdfpsIconPicker.setEnabled(false);
            } else {
                mUdfpsIconPicker.setEnabled(true);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent result) {
       if (requestCode == REQUEST_PICK_IMAGE && resultCode == Activity.RESULT_OK) {
           Uri uri = null;
           if (result != null) {
               uri = result.getData();
               setPickerIcon(uri.toString());
               Settings.System.putString(getContentResolver(), Settings.System.OMNI_CUSTOM_FP_ICON,
                   uri.toString());
            }
        } else if (requestCode == REQUEST_PICK_IMAGE && resultCode == Activity.RESULT_CANCELED) {
            mCustomFPImage.setIcon(new ColorDrawable(Color.TRANSPARENT));
            Settings.System.putString(getContentResolver(), Settings.System.OMNI_CUSTOM_FP_ICON, "");
        }
    }

    private void setPickerIcon(String uri) {
        try {
                ParcelFileDescriptor parcelFileDescriptor =
                    getContext().getContentResolver().openFileDescriptor(Uri.parse(uri), "r");
                FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
                parcelFileDescriptor.close();
                Drawable d = new BitmapDrawable(getResources(), image);
                mCustomFPImage.setIcon(d);
            }
            catch (Exception e) {}
    }
    
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
        new BaseSearchIndexProvider() {
            @Override
            public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                    boolean enabled) {
                final ArrayList<SearchIndexableResource> result = new ArrayList<>();
                final SearchIndexableResource sir = new SearchIndexableResource(context);
                sir.xmlResId = R.xml.customisation;
                result.add(sir);
                return result;
            }

            @Override
            public List<String> getNonIndexableKeys(Context context) {
                final List<String> keys = super.getNonIndexableKeys(context);
                return keys;
            }
    };
}
