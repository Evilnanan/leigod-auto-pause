package com.evilnanan.leigodtoken;

import java.net.URI;

final class TokenPolicy {
    static final String LOGIN_URL = "https://www.leigod.com/activitys/leigodpass2";
    static final String DEFAULT_REPOSITORY = "Evilnanan/leigod-auto-pause";

    static boolean isOfficialPage(String value) {
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "www.leigod.com".equalsIgnoreCase(uri.getHost())
                    && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean validRepository(String value) {
        return value != null && value.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    }

    static final String READ_TOKEN = """
        (function () {
          if (location.origin !== 'https://www.leigod.com') return JSON.stringify({status:'wrong_origin'});
          try {
            var saved = JSON.parse(localStorage.getItem('account_token') || 'null');
            var token = saved && saved.account_token;
            if (typeof token !== 'string' || !token.trim() || /^(null|undefined)$/.test(token.trim())) {
              return JSON.stringify({status:'missing'});
            }
            return JSON.stringify({status:'found', token:token.trim()});
          } catch (error) { return JSON.stringify({status:'invalid_storage'}); }
        })()
        """;
}
