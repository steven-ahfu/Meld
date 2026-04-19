// ==UserScript==
// @name                Mobilism Download Links Clean Bloat
// @namespace           https://greasyfork.org/users/821661
// @match               https://uploadrar.com/*
// @match               https://www.uploadrar.com/*
// @match               https://modsfire.com/*
// @match               https://mega4upload.net/*
// @match               *://upfion.com/*
// @match               *://upfiles.com/*
// @match               *://upfilesgo.com/*
// @match               *://uploady.io/*
// @match               https://www.up-4ever.net/*
// @include             *://frdl.*/*
// @include             *://fredl.*/*
// @include             *://katfile.*/*
// @include             *://dailyuploads.*/*
// @match               https://devuploads.com/*
// @match               https://djxmaza.in/*
// @match               https://smartfeecalculator.com/*
// @match               https://pdfhindibook.com/*
// @match               https://gujjukhabar.in/*
// @match               https://rfiql.com/*
// @match               https://cloudfam.io/*
// @match               https://dropgalaxy.*/*
// @match               https://financemonk.*/*
// @grant               GM.addStyle
// @run-at              document-start
// @version             3.2.0
// @description         Removes unnecessary bloat from multiple file hosting and download sites to make download links more accessible
// @license             GPL-3.0-only
// @downloadURL https://update.greasyfork.org/scripts/556623/Mobilism%20Download%20Links%20Clean%20Bloat.user.js
// @updateURL https://update.greasyfork.org/scripts/556623/Mobilism%20Download%20Links%20Clean%20Bloat.meta.js
// ==/UserScript==

(function () {
    'use strict';

    const domain = window.location.hostname;

    function addCSS(css) {
        document.documentElement.insertAdjacentHTML("beforeend", `<style>${css}</style>`);
    }

    /* ---------------------------------------------------------
     * CLEANER RULES PER HOST
     * --------------------------------------------------------- */

    // UploadRAR
    if (domain === "uploadrar.com" || domain === "www.uploadrar.com") {
        addCSS(`
            .premiumTable,
            .footer,
            .fs-5,
            .mb-4>p,
            .shadow-sm,
            .p-0,
            .my-5,
            .p-0,
            .mb-4>ul,
            .border-warning,
            form[method="POST"]>center,
            .m-2.btn-lg.btn-primary.btn,
            .card-body,
            body>main>center,
            .ad-label,
            .ad-box>center,
            main[style="height: auto !important;"]>center,
            .header-row {
                display: none !important;
            }
            body {
                padding-top: 0 !important;
                margin-top: 0 !important;
            }
        `);
    }

    // ModsFire
    else if (domain === "modsfire.com") {
        addCSS(`
            .navbar,
            .footer,
            .inf-down,
            .report-btn,
            .section-title > h1,
            .disc-tabbing-main {
                display: none !important;
            }
        `);
    }

    // FRDL / FREDL
    else if (domain.startsWith("frdl.") || domain.startsWith("fredl.")) {
        addCSS(`
            .header,
            .seperate,
            .footer,
            .col-lg-12,
            #fdmMsg,
            .btn-warning {
                display: none !important;
            }
        `);
    }

    // KatFile
    else if (domain.startsWith("katfile.")) {
        addCSS(`
            .navbar,
            ul[style="margin: 10px 10px 0 10px;"] > li,
            body > footer,
            img[src="/images/n.png"],
            .sp,
            .free-class > span,
            .style3,
            div[id="container"] > h2,
            .recommended,
            .page-buffer,
            #container > [src],
            .free-class > b,
            #kModal,
            .modal-backdrop,
            .premium-class {
                display: none !important;
            }
        `);
    }

    // Mega4Upload
    else if (domain === "mega4upload.net") {
        addCSS(`
            .section-title,
            .p-3 > center,
            .d-md-none,
            .card,
            .footer-copyright,
            #app-header,
            .list-group,
            .compare_table,
            .app-footer {
                display: none !important;
            }
        `);
    }

    // Upfion / Upfiles / Upfilesgo
    else if (domain === "upfion.com" || domain === "upfiles.com" || domain === "upfilesgo.com") {
        addCSS(`
            .file-title,
            body > header,
            body > footer,
            div[style="padding: 0 40px; margin-top: 50px"],
            .features,
            .premium,
            .container>hr,
            .speed,
            .faqs {
                display: none !important;
            }

            /* Anti-adblock overlays / modals — hide before they can freeze the page */
            #adblock, #adblock-detected, #adblock-modal, #adblock-overlay,
            #adBlocked, #adblocker, #ab-modal, #abModal, #abDetect,
            .adblock, .adblock-detected, .adblock-modal, .adblock-overlay,
            .adblock-warning, .adblocker, .ab-modal, .ab-overlay, .ab-detect,
            [class*="adblock" i], [id*="adblock" i],
            [class*="ad-block" i], [id*="ad-block" i],
            [class*="blockadblock" i], [id*="blockadblock" i] {
                display: none !important;
                visibility: hidden !important;
                opacity: 0 !important;
                pointer-events: none !important;
            }

            /* Prevent the page from being locked behind a modal backdrop */
            html, body {
                overflow: auto !important;
                position: static !important;
                filter: none !important;
            }
        `);

        bypassUpfilesAntiAdblock();
    }

    // DailyUploads
    else if (domain.includes("dailyuploads")) {
        addCSS(`
            footer[style="font-size:13px; position:fixed; bottom:0; margin-top:5000px; height:10px; width:100%; padding-top:2px;"],
            .sharetabs,
            #header,
            .downleft,
            a[href^="https://dailyuploads.net/"],
            #open,
            h2[style="color:#da2017;"] {
                display: none !important;
            }
        `);
    }

    // CloudFam
    else if (domain === "cloudfam.io" || domain === "www.cloudfam.io") {
        addCSS(`
            .border-b,
            .premium,
            .text-center,
            .cf-footer,
            .cf-header,
            .cf-main>center,
            .text-xs {
                display: none !important;
            }
        `);
    }

    // DropGalaxy & FinanceMonk
    else if (domain.startsWith("dropgalaxy.") || domain.startsWith("financemonk.")) {
        addCSS(`
            .pt-4,
            #a-ads5,
            .float-right,
            .float-left,
            .icon,
            .btn-danger,
            .text-primary,
            .premium-feed-card,
            #a-ads6,
            .mys-wrapper,
            #tokennstatus,
            #sharetabs,
            .footer,
            iframe[width="280"],
            .py-3,
            .navbar {
                display: none !important;
            }
        `);
    }

    // Uploady
    else if (domain === "uploady.io" || domain === "www.uploady.io") {
        addCSS(`
            .header-row,
            .premium,
            .danger,
            .negative,
            #free-header,
            .upgrade-cta,
            #premiumplans,
            .footer,
            .dl-wait-upsell,
            .captcha-upsell,
            .col-md-4,
            .col-6,
            .align-items-start,
            .mt-3,
            .mt-2 {
                display: none !important;
            }
        `);
    }

    // Up-4ever
    else if (domain === "www.up-4ever.net") {
        addCSS(`
            #app-header,
            #app-footer,
            .text-xl,
            .د-flex,
            .space-y-2,
            .mt-5,
            .custom-accordion,
            .tp-faq-left-wrapper,
            .breadcrumb__area,
            .file-card,
            svg[fill="var(--bs-secondary)"],
            svg[fill="var(--bs-dark)"],
            .col-xl-8,
            input[name="method_premium"],
            div>div>span,
            .ph-check,
            .text-dark,
            .justify-content-center,
            .tp-price-table {
                display: none !important;
            }
        `);
    }

    /* ---------------------------------------------------------
     * UPFILES FAMILY ANTI-ADBLOCK BYPASS
     *
     * upfilesgo.com 302-redirects to upfiles.com. Both domains ship a
     * FuckAdBlock/BlockAdBlock-style detector that blocks the "Get Link"
     * button when an ad blocker (or the bloat-cleaner CSS above) trips its
     * bait-element check. The logic below spoofs the detector so the page
     * believes no blocker is present.
     * --------------------------------------------------------- */
    function bypassUpfilesAntiAdblock() {
        const notDetected = function (cb) { if (typeof cb === 'function') try { cb(false); } catch (e) {} };
        const noop = function () {};
        const self = {};
        const fakeAdBlock = {
            _options: { checkOnLoad: false, resetOnEnd: false, loopCheckTime: 50, loopMaxNumber: 5, baitClass: 'pub_300x250 pub_300x250m pub_728x90 text-ad textAd text_ad text_ads text-ads text-ad-links', baitStyle: 'width: 1px !important; height: 1px !important; position: absolute !important; left: -10000px !important; top: -1000px !important;' },
            _var: { version: '3.2.1', bait: null, checking: false, loop: null, loopNumber: 0, event: { detected: [], notDetected: [] } },
            setOption: function () { return this; },
            check: function (loop, cb) { notDetected(cb); return false; },
            clearEvent: function () { return this; },
            on: function (detected, cb) { if (!detected) notDetected(cb); return this; },
            onDetected: function () { return this; },
            onNotDetected: function (cb) { notDetected(cb); return this; },
            emitEvent: function () { return this; },
            addCustomEvent: noop
        };

        function lockProp(target, key, value) {
            try {
                Object.defineProperty(target, key, {
                    configurable: false,
                    get: function () { return value; },
                    set: function () {}
                });
            } catch (e) {
                try { target[key] = value; } catch (_) {}
            }
        }

        // Common FuckAdBlock/BlockAdBlock entry points
        lockProp(window, 'fuckAdBlock', fakeAdBlock);
        lockProp(window, 'blockAdBlock', fakeAdBlock);
        lockProp(window, 'sniffAdBlock', fakeAdBlock);
        const FakeCtor = function () { return fakeAdBlock; };
        lockProp(window, 'FuckAdBlock', FakeCtor);
        lockProp(window, 'BlockAdBlock', FakeCtor);
        lockProp(window, 'SniffAdBlock', FakeCtor);

        // Boolean / numeric flags common in ad-block detection scripts
        lockProp(window, 'canRunAds', true);
        lockProp(window, 'adsbygoogle', (window.adsbygoogle && window.adsbygoogle.push) ? window.adsbygoogle : { loaded: true, push: function () { return 0; } });
        ['adblock', 'adBlock', 'adBlockEnabled', 'adBlockDetected', 'adBlockerDetected',
         'adsBlocked', 'adBlocked', 'isAdblock', 'isAdBlockActive', 'isAdBlockEnabled',
         'adb', 'AdB', 'hasAdBlock'].forEach(function (k) { lockProp(window, k, false); });

        // Some scripts probe `document.getElementById('bait').offsetHeight === 0` —
        // force any freshly-inserted bait element to report non-zero dimensions.
        const dimensionProps = ['offsetHeight', 'offsetWidth', 'clientHeight', 'clientWidth'];
        dimensionProps.forEach(function (prop) {
            try {
                const orig = Object.getOwnPropertyDescriptor(HTMLElement.prototype, prop);
                if (!orig || !orig.get) return;
                Object.defineProperty(HTMLElement.prototype, prop, {
                    configurable: true,
                    get: function () {
                        const cls = (this.className && this.className.baseVal) || this.className || '';
                        const id = this.id || '';
                        const signature = (typeof cls === 'string' ? cls : '') + ' ' + id;
                        if (/pub_\d+x\d+|text[-_]?ads?|ad[-_]?(banner|box|bait)|adsbox|adsbygoogle/i.test(signature)) {
                            return 10;
                        }
                        return orig.get.call(this);
                    }
                });
            } catch (e) {}
        });

        // getComputedStyle is another common detection surface. Detectors read
        // properties both directly (`style.display`) and through the
        // CSSStyleDeclaration methods (`style.getPropertyValue('display')`,
        // `style.getPropertyPriority('display')`), so every access path has to
        // report the bait element as visible.
        const spoofedVisible = {
            display: 'block',
            visibility: 'visible',
            opacity: '1'
        };
        const originalGetComputedStyle = window.getComputedStyle;
        window.getComputedStyle = function (el, pseudo) {
            const style = originalGetComputedStyle.call(this, el, pseudo);
            if (el && el.nodeType === 1) {
                const cls = (el.className && el.className.baseVal) || el.className || '';
                const id = el.id || '';
                const signature = (typeof cls === 'string' ? cls : '') + ' ' + id;
                if (/pub_\d+x\d+|text[-_]?ads?|ad[-_]?(banner|box|bait)|adsbox|adsbygoogle/i.test(signature)) {
                    return new Proxy(style, {
                        get: function (target, prop) {
                            if (typeof prop === 'string' && prop in spoofedVisible) {
                                return spoofedVisible[prop];
                            }
                            if (prop === 'getPropertyValue') {
                                return function (name) {
                                    const key = typeof name === 'string' ? name.toLowerCase() : '';
                                    if (key in spoofedVisible) return spoofedVisible[key];
                                    return target.getPropertyValue(name);
                                };
                            }
                            if (prop === 'getPropertyPriority') {
                                return function (name) {
                                    const key = typeof name === 'string' ? name.toLowerCase() : '';
                                    if (key in spoofedVisible) return '';
                                    return target.getPropertyPriority(name);
                                };
                            }
                            const v = target[prop];
                            return typeof v === 'function' ? v.bind(target) : v;
                        }
                    });
                }
            }
            return style;
        };

        // Remove any overlay that slips through the CSS (e.g. dynamically-created
        // wrappers with no stable class). Also restore scroll-locking styles.
        const overlayPattern = /adblock|ad-block|blockadblock|adb-modal|adb-overlay/i;
        const overlayTags = { DIV: 1, SECTION: 1, ASIDE: 1 };

        function matchesOverlay(el) {
            if (!el || el.nodeType !== 1 || !overlayTags[el.tagName]) return false;
            const cls = typeof el.className === 'string' ? el.className : '';
            const id = el.id || '';
            return overlayPattern.test(cls) || overlayPattern.test(id);
        }

        function inspectNode(node) {
            if (!node || node.nodeType !== 1) return;
            if (matchesOverlay(node)) {
                node.remove();
                return;
            }
            // Overlay can be nested under the added subtree; scope the scan to it
            // so we avoid the whole-document walk on every mutation.
            if (node.querySelectorAll) {
                node.querySelectorAll('div, section, aside').forEach(function (el) {
                    if (matchesOverlay(el)) el.remove();
                });
            }
        }

        function restoreScrollStyles() {
            if (document.body) {
                document.body.style.overflow = '';
                document.body.style.position = '';
            }
            document.documentElement.style.overflow = '';
        }

        function initialCleanup() {
            document.querySelectorAll('div, section, aside').forEach(function (el) {
                if (matchesOverlay(el)) el.remove();
            });
            restoreScrollStyles();
        }

        function onMutations(records) {
            for (let i = 0; i < records.length; i++) {
                const record = records[i];
                for (let j = 0; j < record.addedNodes.length; j++) {
                    inspectNode(record.addedNodes[j]);
                }
            }
            restoreScrollStyles();
        }

        function startObserver() {
            initialCleanup();
            new MutationObserver(onMutations).observe(document.documentElement, {
                childList: true,
                subtree: true
            });
        }

        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', startObserver, { once: true });
        } else {
            startObserver();
        }
    }

    /* ---------------------------------------------------------
     * DEVUPLOADS SPECIAL CLEANER
     * --------------------------------------------------------- */

    function devuploadsCSS() {
        addCSS(`
            body:not(:has(#container)) {
                background-color: #131316 !important;
            }
            body {
                overflow: hidden !important;
            }
            #folders_paging {
                display: none !important;
            }
            #container {
                max-width: unset !important;
                position: fixed !important;
                inset: 0 !important;
                display: flex !important;
                flex-direction: column !important;
                justify-content: center !important;
                align-items: center !important;
                background-color: #131316 !important;
                margin: 0 !important;
                z-index: 214748364 !important;
            }
        `);
    }

    function reDevuploadsCSS() {
        addCSS(`
            #dlp,
            [style*="block"]:is(#dlndiv, #adBlocked, #Blocked) {
                position: fixed !important;
                height: 100vh !important;
                width: 100vw !important;
                inset: 0 !important;
                margin: 0 !important;
                padding: 0 !important;
                background-color: #131316 !important;
                z-index: 99999 !important;
                display: flex !important;
                flex-direction: column !important;
                justify-content: center !important;
                align-items: center !important;
            }
        `);
    }

    if (domain === "devuploads.com") {
        devuploadsCSS();
    } else if (
        domain === "djxmaza.in" ||
        domain === "smartfeecalculator.com" ||
        domain === "gujjukhabar.in" ||
        domain === "pdfhindibook.com" ||
        domain === "rfiql.com"
    ) {
        reDevuploadsCSS();
    }

})();
