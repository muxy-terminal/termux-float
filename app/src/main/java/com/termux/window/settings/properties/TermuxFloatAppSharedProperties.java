package com.termux.window.settings.properties;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.properties.LegacyImeProperties;

import java.io.File;
import java.io.IOException;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;

public class TermuxFloatAppSharedProperties extends TermuxSharedProperties {

    private static final String LOG_TAG = "TermuxFloatAppSharedProperties";

    public TermuxFloatAppSharedProperties(@NonNull Context context) {
        super(context, TermuxConstants.TERMUX_FLOAT_APP_NAME,
                TermuxConstants.TERMUX_FLOAT_PROPERTIES_FILE_PATHS_LIST,
                TermuxPropertyConstants.TERMUX_APP_PROPERTIES_LIST,
                new SharedPropertiesParserClient());
    }

    @Override
    public synchronized void loadTermuxPropertiesFromDisk() {
        for (String path : TermuxConstants.TERMUX_FLOAT_PROPERTIES_FILE_PATHS_LIST) {
            try {
                LegacyImeProperties.removeFromFile(new File(path));
            } catch (IOException error) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Cannot remove obsolete IME settings from " + path, error);
                Logger.logErrorAndShowToast(mContext, LOG_TAG, "Could not update termux.float.properties");
            }
        }
        super.loadTermuxPropertiesFromDisk();
    }

}
