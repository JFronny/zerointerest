package dev.jfronnz.zerointerest;

import androidx.compose.ui.platform.DesktopUriHandler;
import androidx.compose.ui.platform.UriHandler;

public class InternalHelper {
    public static UriHandler getUriHandler() {
        return new DesktopUriHandler();
    }
}
