package com.evilnanan.leigodtoken;

/** Adapt only the official login dialog; keep the site's login/captcha handlers. */
final class PageLayout {
    static final String SCRIPT = """
        (function () {
          if (location.origin !== 'https://www.leigod.com') return;
          if (window.__leigodTokenLayoutV1) { window.__leigodTokenLayoutV1.apply(); return; }
          var viewportContent = 'width=device-width, initial-scale=1, minimum-scale=0.25, maximum-scale=5, user-scalable=yes, viewport-fit=cover';
          var css = `
            .token-helper-login-mask {
              align-items:flex-start!important; justify-content:center!important;
              padding:8px!important; box-sizing:border-box!important;
              overflow:auto!important; touch-action:pan-x pan-y pinch-zoom!important;
              -webkit-overflow-scrolling:touch!important;
            }
            .token-helper-login-shell {
              width:calc(100vw - 16px)!important; max-width:480px!important; min-width:0!important;
              height:auto!important; min-height:0!important; max-height:none!important;
              margin:0 auto!important; flex-shrink:0!important; transform:none!important;
              position:relative!important; top:auto!important; left:auto!important;
              overflow:visible!important; box-sizing:border-box!important;
            }
            .token-helper-login-shell .leigod-modal-header {flex-shrink:0!important}
            .token-helper-login-shell .leigod-modal-body {
              height:auto!important; min-height:0!important; overflow:visible!important; flex:0 0 auto!important;
            }
            .token-helper-login-shell .login-modal {
              display:block!important; width:100%!important; height:auto!important; min-height:0!important;
              overflow:visible!important; background:#1c1c1c!important; padding:0!important;
            }
            .token-helper-login-shell .login-modal > :not(.login-modal-content) {display:none!important}
            .token-helper-login-shell .login-modal-content {
              width:100%!important; max-width:none!important; min-width:0!important;
              height:auto!important; min-height:0!important; margin:0!important;
              padding:36px 16px 16px!important; box-sizing:border-box!important; overflow:visible!important;
            }
            .token-helper-login-shell .phone-login,
            .token-helper-login-shell .pwd-login,
            .token-helper-login-shell .forget-login,
            .token-helper-login-shell form {width:100%!important; min-width:0!important; box-sizing:border-box!important}
            .token-helper-login-shell .handle-bottom {
              position:static!important; margin-top:20px!important; padding:4px 0!important;
              min-height:40px!important; height:auto!important;
            }
            .token-helper-login-shell input {font-size:16px!important}
          `;
          var apply = function () {
            var meta = document.querySelector('meta[name="viewport"]');
            if (!meta) { meta = document.createElement('meta'); meta.name = 'viewport'; document.head.appendChild(meta); }
            if (meta.content !== viewportContent) meta.content = viewportContent;
            if (!document.getElementById('token-helper-login-style')) {
              var style = document.createElement('style'); style.id = 'token-helper-login-style';
              style.textContent = css; document.head.appendChild(style);
            }
            document.querySelectorAll('.login-modal').forEach(function (login) {
              var shell = login.closest('.leigod-modal');
              if (shell) shell.classList.add('token-helper-login-shell');
              var mask = login.closest('.leigod-modal-mask');
              if (mask) mask.classList.add('token-helper-login-mask');
            });
          };
          var pending = false;
          var schedule = function () {
            if (pending) return; pending = true;
            requestAnimationFrame(function () { pending = false; apply(); });
          };
          var revealFocus = function () {
            var active = document.activeElement;
            if (active && active.closest('.token-helper-login-shell')) {
              active.scrollIntoView({block:'center', inline:'nearest'});
            }
          };
          var observer = new MutationObserver(schedule);
          observer.observe(document.documentElement, {childList:true, subtree:true, attributes:true, attributeFilter:['content']});
          document.addEventListener('focusin', function () { setTimeout(revealFocus, 120); });
          window.addEventListener('resize', function () { schedule(); setTimeout(revealFocus, 120); });
          if (window.visualViewport) window.visualViewport.addEventListener('resize', function () { setTimeout(revealFocus, 120); });
          window.__leigodTokenLayoutV1 = {apply:apply};
          apply();
        })()
        """;
}
