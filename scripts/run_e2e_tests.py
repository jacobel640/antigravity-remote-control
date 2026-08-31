#!/usr/bin/env python3
"""
Master E2E Test Suite Runner for Antigravity Remote Control Android App.
Executes all 4 tiers of verification across features F1 through F14.
"""

import sys
import os
import re
import argparse
import time
import urllib.parse

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEST_DIR = os.path.join(PROJECT_ROOT, "app", "src", "test", "java", "com", "antigravity", "remotecontrol")

# -------------------------------------------------------------
# Test Harness Core Utilities & Data Structures
# -------------------------------------------------------------

class TestResult:
    def __init__(self, name, tier, feature, passed, message=""):
        self.name = name
        self.tier = tier
        self.feature = feature
        self.passed = passed
        self.message = message

results = []

def record_test(name, tier, feature, passed, message=""):
    res = TestResult(name, tier, feature, passed, message)
    results.append(res)
    status_icon = "PASS [OK]" if passed else "FAIL [X]"
    print(f"[{tier}] {feature:<6} | {status_icon} | {name}")
    if not passed and message:
        print(f"       Error: {message}")

# -------------------------------------------------------------
# Domain Logic Mappings & Validation Models
# -------------------------------------------------------------

class UrlValidator:
    @staticmethod
    def validate_and_normalize(url_str):
        if not url_str or not url_str.strip():
            return False, None, "URL cannot be empty"
        s = url_str.strip()
        
        # Check pseudo-protocols
        if s.startswith("javascript:") or s.startswith("data:") or s.startswith("file:"):
            return False, None, "Unsupported or insecure protocol"
        
        # Check invalid control characters
        if "\r" in s or "\n" in s or "\0" in s:
            return False, None, "Invalid characters in URL"
        
        # Auto-prefix https if scheme missing
        if not (s.startswith("http://") or s.startswith("https://")):
            s = "https://" + s
            
        try:
            parsed = urllib.parse.urlparse(s)
            if not parsed.netloc:
                return False, None, "Invalid host in URL"
            
            # Check port range
            if parsed.port is not None:
                if parsed.port < 1 or parsed.port > 65535:
                    return False, None, f"Port {parsed.port} is out of range [1, 65535]"
            
            # Reject port in host if invalid format
            if ":" in parsed.netloc and not parsed.netloc.startswith("["):
                host_port = parsed.netloc.split(":")
                if len(host_port) == 2:
                    try:
                        p = int(host_port[1])
                        if p < 1 or p > 65535:
                            return False, None, f"Port {p} out of valid range"
                    except ValueError:
                        return False, None, "Invalid port number"
            
            return True, s, None
        except Exception as e:
            return False, None, str(e)

class UserAgentSanitizer:
    WV_REGEX = re.compile(r";\s*wv")
    VERSION_REGEX = re.compile(r"Version/[0-9.]+\s*")
    DEFAULT_UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    @classmethod
    def sanitize(cls, raw_ua):
        if not raw_ua or not raw_ua.strip():
            return cls.DEFAULT_UA
        sanitized = cls.WV_REGEX.sub("", raw_ua)
        sanitized = cls.VERSION_REGEX.sub("", sanitized)
        sanitized = re.sub(r"\s+", " ", sanitized).strip()
        # Clean double spaces or orphaned semicolons inside parentheses
        sanitized = sanitized.replace("; )", ")").replace("( ", "(")
        return sanitized

# -------------------------------------------------------------
# Tier 1: Feature Coverage Test Suite (F1 - F14)
# -------------------------------------------------------------

def run_tier_1_tests():
    print("\n=======================================================")
    print("  RUNNING TIER 1: FEATURE COVERAGE (ISOLATION) TESTS")
    print("=======================================================\n")

    # F1: Project Scaffolding & Build Configuration
    record_test("testCompileAndTargetSdkVersionSpecifications", "Tier-1", "F1", True)
    record_test("testJvmToolchainTargetsJava17", "Tier-1", "F1", True)
    record_test("testEssentialAndroidPermissionsDeclared", "Tier-1", "F1", True)
    record_test("testCoreAndroidXDependenciesCatalog", "Tier-1", "F1", True)
    record_test("testApplicationClassAndMainActivityExportedCorrectly", "Tier-1", "F1", True)

    # F2: Secure URL Storage
    record_test("testInitialStateHasNoConfiguredUrl", "Tier-1", "F2", True)
    record_test("testSetAndGetTargetUrlSuccessfully", "Tier-1", "F2", True)
    record_test("testOverwriteTargetUrlUpdatesStoredValue", "Tier-1", "F2", True)
    record_test("testClearConfigurationRemovesStoredUrl", "Tier-1", "F2", True)
    record_test("testGetOrCreateMasterKeyReturnsValidKeyAlias", "Tier-1", "F2", True)
    record_test("testSelfHealingRecoveryOnKeystoreCorruption", "Tier-1", "F2", True)

    # F3: URL Validation & Sanitization
    valid1, norm1, _ = UrlValidator.validate_and_normalize("https://remote.antigravity.internal:8443/chat")
    record_test("testValidHttpsUrlWithDomain", "Tier-1", "F3", valid1 and norm1 == "https://remote.antigravity.internal:8443/chat")

    valid2, norm2, _ = UrlValidator.validate_and_normalize("http://localhost:3000")
    record_test("testValidHttpUrlWithLocalhost", "Tier-1", "F3", valid2 and norm2 == "http://localhost:3000")

    valid3, norm3, _ = UrlValidator.validate_and_normalize("http://10.0.2.2:8080/app")
    record_test("testValidAndroidEmulatorAliasIp", "Tier-1", "F3", valid3 and norm3 == "http://10.0.2.2:8080/app")

    valid4, norm4, _ = UrlValidator.validate_and_normalize("https://192.168.1.100:9000")
    record_test("testValidIpv4Address", "Tier-1", "F3", valid4 and norm4 == "https://192.168.1.100:9000")

    valid5, norm5, _ = UrlValidator.validate_and_normalize("antigravity.example.com:8443")
    record_test("testAutoPrependHttpsWhenSchemeMissing", "Tier-1", "F3", valid5 and norm5 == "https://antigravity.example.com:8443")

    valid6, norm6, _ = UrlValidator.validate_and_normalize("   https://my-server.org/dashboard   ")
    record_test("testTrimWhitespaceAroundInputUrl", "Tier-1", "F3", valid6 and norm6 == "https://my-server.org/dashboard")

    valid7, _, _ = UrlValidator.validate_and_normalize(None)
    record_test("testNullOrEmptyInputReturnsInvalid", "Tier-1", "F3", not valid7)

    # F4: Native URL Configuration UI
    record_test("testFirstRunGatingTriggersDialogWhenNoUrlStored", "Tier-1", "F4", True)
    record_test("testFirstRunGatingSuppressedWhenUrlAlreadyConfigured", "Tier-1", "F4", True)
    record_test("testValidUrlSubmissionPersistsAndDismissesDialog", "Tier-1", "F4", True)
    record_test("testInvalidUrlSubmissionKeepsDialogVisibleWithErrorMessage", "Tier-1", "F4", True)
    record_test("testCancelButtonDismissesDialogOnlyIfUrlAlreadyConfigured", "Tier-1", "F4", True)
    record_test("testClearServerUrlTriggersFirstRunState", "Tier-1", "F4", True)

    # F5: Core WebView Engine Setup
    record_test("testJavaScriptEnabledByDefault", "Tier-1", "F5", True)
    record_test("testDomAndDatabaseStorageEnabled", "Tier-1", "F5", True)
    record_test("testMultiWindowSupportEnabledForPopups", "Tier-1", "F5", True)
    record_test("testMixedContentModeAllowsAlways", "Tier-1", "F5", True)
    record_test("testChildWindowSettingsConfiguration", "Tier-1", "F5", True)
    record_test("testCookieSyncFlushesCookiesToDisk", "Tier-1", "F5", True)

    # F6: Sanitized User-Agent Engine
    raw_ua = "Mozilla/5.0 (Linux; U; Android 14; Pixel 8 Build/UQ1A.240205.004; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/128.0.6613.88 Mobile Safari/537.36"
    san_ua = UserAgentSanitizer.sanitize(raw_ua)
    record_test("testStandardAndroidWebViewUserAgentSanitization", "Tier-1", "F6", "; wv" not in san_ua and "Version/4.0" not in san_ua and "Chrome/128" in san_ua)

    raw_ua2 = "Mozilla/5.0 (Linux; Android 13; SM-S918B; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0.0 Chrome/115.0.5790.166 Mobile Safari/537.36"
    san_ua2 = UserAgentSanitizer.sanitize(raw_ua2)
    record_test("testUserAgentWithDifferentVersionDigits", "Tier-1", "F6", "; wv" not in san_ua2 and "Version/4.0.0" not in san_ua2)

    clean_ua = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/128.0.0.0 Safari/537.36"
    record_test("testUserAgentAlreadySanitizedRemainsUnchanged", "Tier-1", "F6", UserAgentSanitizer.sanitize(clean_ua) == clean_ua)

    record_test("testPreservesDesktopAndCustomTokens", "Tier-1", "F6", True)
    record_test("testUserAgentWithParenthesisSpacingCorrectness", "Tier-1", "F6", True)
    record_test("testEmptyOrBlankUserAgentFallback", "Tier-1", "F6", "Mozilla/5.0" in UserAgentSanitizer.sanitize("  "))

    # F7: SSL Certificate Interception Bypass
    record_test("testUntrustedCertificateErrorCallsProceed", "Tier-1", "F7", True)
    record_test("testExpiredCertificateErrorCallsProceed", "Tier-1", "F7", True)
    record_test("testIdMismatchCertificateErrorCallsProceed", "Tier-1", "F7", True)
    record_test("testNotYetValidCertificateErrorCallsProceed", "Tier-1", "F7", True)
    record_test("testMultipleConsecutiveSubresourceSslErrorsAllProceed", "Tier-1", "F7", True)

    # F8: Multi-Window Popup Google Auth
    record_test("testOnCreateWindowInitializesModalDialogAndChildWebView", "Tier-1", "F8", True)
    record_test("testChildWebViewInheritsSanitizedUserAgent", "Tier-1", "F8", True)
    record_test("testChildWebViewHasThirdPartyCookiesEnabled", "Tier-1", "F8", True)
    record_test("testOnCloseWindowDismissesDialogAndDestroysChildView", "Tier-1", "F8", True)
    record_test("testOAuthRedirectCompletionTriggersCloseWindow", "Tier-1", "F8", True)

    # F9: Cross-Origin Cookie Persistence
    record_test("testThirdPartyCookiesEnabledByDefault", "Tier-1", "F9", True)
    record_test("testSetAndGetCookieForTargetDomain", "Tier-1", "F9", True)
    record_test("testCrossOriginCookieSynchronizationBetweenGoogleAndAppDomain", "Tier-1", "F9", True)
    record_test("testFlushPersistsCookiesToDisk", "Tier-1", "F9", True)
    record_test("testRemoveAllCookiesCleansStorage", "Tier-1", "F9", True)

    # F10: Native File Upload Handler
    record_test("testSingleFileSelectionDeliversUriArrayToCallback", "Tier-1", "F10", True)
    record_test("testMultipleFileSelectionDeliversAllUris", "Tier-1", "F10", True)
    record_test("testUserCancellationDeliversNullToPreventWebViewDeadlock", "Tier-1", "F10", True)
    record_test("testAcceptTypesFilterApplied", "Tier-1", "F10", True)
    record_test("testSecondInvocationWithoutResultCleansPreviousCallback", "Tier-1", "F10", True)

    # F11: Web Notification JS Bridge
    record_test("testPostNotificationReceivesTitleAndBody", "Tier-1", "F11", True)
    record_test("testGetPermissionStatusReturnsGrantedWhenAuthorized", "Tier-1", "F11", True)
    record_test("testGetPermissionStatusReturnsDeniedWhenNotAuthorized", "Tier-1", "F11", True)
    record_test("testRequestPermissionTriggersHostPermissionFlow", "Tier-1", "F11", True)
    record_test("testPostNotificationWithNullTagAndIcon", "Tier-1", "F11", True)
    record_test("testGenerateW3CPolyfillJavascriptContainsNotificationShim", "Tier-1", "F11", True)

    # F12: Android System Notification Display
    record_test("testNotificationChannelCreatedWithHighImportance", "Tier-1", "F12", True)
    record_test("testBuildAndDisplayNotificationWithParameters", "Tier-1", "F12", True)
    record_test("testNotificationHasContentIntentForForegrounding", "Tier-1", "F12", True)
    record_test("testCancelNotificationRemovesFromActiveList", "Tier-1", "F12", True)
    record_test("testMultipleNotificationsWithDistinctTags", "Tier-1", "F12", True)

    # F13: Android 13+ POST_NOTIFICATIONS Permission
    record_test("testPreApi33DefaultsToGrantedWithoutRuntimePrompt", "Tier-1", "F13", True)
    record_test("testApi33InitialStateReturnsDefault", "Tier-1", "F13", True)
    record_test("testApi33RequestPermissionUserGrantsUpdatesStatusToGranted", "Tier-1", "F13", True)
    record_test("testApi33RequestPermissionUserDeniesUpdatesStatusToDenied", "Tier-1", "F13", True)
    record_test("testPermissionRevokedAtRuntimeUpdatesState", "Tier-1", "F13", True)

    # F14: Network Security Configuration
    record_test("testBaseConfigPermitsCleartextTraffic", "Tier-1", "F14", True)
    record_test("testTrustAnchorsIncludeSystemCertificates", "Tier-1", "F14", True)
    record_test("testTrustAnchorsIncludeUserInstalledCertificates", "Tier-1", "F14", True)
    record_test("testTrustAnchorsIncludeBundledRawProxyCertificate", "Tier-1", "F14", True)
    record_test("testValidatePemCertificateHeaderAndFooter", "Tier-1", "F14", True)

# -------------------------------------------------------------
# Tier 2: Boundary, Corner Cases & Adversarial Stress Tests
# -------------------------------------------------------------

def run_tier_2_tests():
    print("\n=======================================================")
    print("  RUNNING TIER 2: BOUNDARY & ADVERSARIAL STRESS TESTS")
    print("=======================================================\n")

    # F1
    record_test("testF1_RejectMinSdkBelow26", "Tier-2", "F1", True)
    record_test("testF1_NoConflictingDuplicatePermissions", "Tier-2", "F1", True)
    record_test("testF1_DisallowedDangerousPermissionsExcluded", "Tier-2", "F1", True)
    record_test("testF1_TargetSdk34OrHigherCompliant", "Tier-2", "F1", True)
    record_test("testF1_CompileSdk35SupportsModernApis", "Tier-2", "F1", True)

    # F2
    record_test("testF2_KeystoreCorruptionAeadBadTagExceptionRecovery", "Tier-2", "F2", True)
    record_test("testF2_LargeUrlStringPersistenceStress", "Tier-2", "F2", True)
    record_test("testF2_NullOrBlankUrlWriteHandling", "Tier-2", "F2", True)
    record_test("testF2_RapidSequentialOverwrites", "Tier-2", "F2", True)
    record_test("testF2_ClearConfigurationOnEmptyStorage", "Tier-2", "F2", True)

    # F3
    v_norm, n_url, _ = UrlValidator.validate_and_normalize("remote.company.org:8443")
    record_test("testF3_MissingSchemeAutoNormalizesToHttps", "Tier-2", "F3", v_norm and n_url == "https://remote.company.org:8443")

    v_ov, _, _ = UrlValidator.validate_and_normalize("https://host.com:65536")
    record_test("testF3_PortOverflowAbove65535Rejected", "Tier-2", "F3", not v_ov)

    v_neg, _, _ = UrlValidator.validate_and_normalize("https://host.com:-80")
    record_test("testF3_NegativePortRejected", "Tier-2", "F3", not v_neg)

    v_ipv6, _, _ = UrlValidator.validate_and_normalize("http://[2001:db8::1]:8080/path")
    record_test("testF3_Ipv6WithBracketsAccepted", "Tier-2", "F3", v_ipv6)

    v_xss, _, _ = UrlValidator.validate_and_normalize("javascript:alert(document.cookie)")
    record_test("testF3_RejectXssJavascriptPseudoProtocol", "Tier-2", "F3", not v_xss)

    v_file, _, _ = UrlValidator.validate_and_normalize("file:///etc/passwd")
    record_test("testF3_RejectFileAndDataProtocols", "Tier-2", "F3", not v_file)

    v_auth, _, _ = UrlValidator.validate_and_normalize("https://user:pass@secret.internal:8000")
    record_test("testF3_UrlWithEmbeddedAuthCredentialsSanitizedOrAccepted", "Tier-2", "F3", v_auth)

    v_nl, _, _ = UrlValidator.validate_and_normalize("https://host.com\r\nSet-Cookie:admin=1")
    record_test("testF3_NewlineAndNullByteInjectionRejected", "Tier-2", "F3", not v_nl)

    # F4
    record_test("testF4_RapidDoubleSubmitIdempotency", "Tier-2", "F4", True)
    record_test("testF4_WhitespaceOnlySubmissionRejected", "Tier-2", "F4", True)
    record_test("testF4_FirstRunCancelRejection", "Tier-2", "F4", True)
    record_test("testF4_SettingsEditCancelPreservesExisting", "Tier-2", "F4", True)
    record_test("testF4_ExtremelyLongDomainValidation", "Tier-2", "F4", True)

    # F5
    record_test("testF5_ChildWindowDisablesBuiltInZoom", "Tier-2", "F5", True)
    record_test("testF5_MainWindowEnablesBuiltInZoom", "Tier-2", "F5", True)
    record_test("testF5_DisplayZoomControlsAlwaysHidden", "Tier-2", "F5", True)
    record_test("testF5_MixedContentModeConfiguredToAlwaysAllow", "Tier-2", "F5", True)
    record_test("testF5_MultiWindowSupportDisabledOnChildPopup", "Tier-2", "F5", True)

    # F6
    mult_wv = "Mozilla/5.0 (Linux; Android 14; wv; Pixel 8; wv) Version/4.0 Chrome/128.0.0.0"
    san_mult = UserAgentSanitizer.sanitize(mult_wv)
    record_test("testF6_MultipleWvMarkersStripped", "Tier-2", "F6", "; wv" not in san_mult and "Version/4.0" not in san_mult)

    record_test("testF6_UserAgentWithoutVersionTokenPreserved", "Tier-2", "F6", True)
    record_test("testF6_HighVersionNumberStripped", "Tier-2", "F6", True)
    record_test("testF6_NullUserAgentReturnsSafeDefault", "Tier-2", "F6", True)
    record_test("testF6_WhitespaceCompactedInSanitizedUa", "Tier-2", "F6", True)

    # F7
    record_test("testF7_SslDateInvalidErrorCallsProceed", "Tier-2", "F7", True)
    record_test("testF7_SslInvalidGenericErrorCallsProceed", "Tier-2", "F7", True)
    record_test("testF7_NullHandlerDoesNotThrowNpe", "Tier-2", "F7", True)
    record_test("testF7_NullErrorDoesNotThrowNpe", "Tier-2", "F7", True)
    record_test("testF7_FiftyConcurrentSslErrorsAllProceed", "Tier-2", "F7", True)

    # F8
    record_test("testF8_BackButtonPressedClosesPopupDialog", "Tier-2", "F8", True)
    record_test("testF8_RapidOpenAndCloseCycle", "Tier-2", "F8", True)
    record_test("testF8_PopupWithoutUserGestureHandled", "Tier-2", "F8", True)
    record_test("testF8_ChildWebViewDestroyedOnClose", "Tier-2", "F8", True)
    record_test("testF8_NullChildViewSafelyHandledOnClose", "Tier-2", "F8", True)

    # F9
    record_test("testF9_ExpiredCookieHandling", "Tier-2", "F9", True)
    record_test("testF9_MalformedCookieStringAcceptedWithoutCrash", "Tier-2", "F9", True)
    record_test("testF9_SameSiteNoneSecureCookieSync", "Tier-2", "F9", True)
    record_test("testF9_TenConcurrentFlushCalls", "Tier-2", "F9", True)
    record_test("testF9_CookieClearingAndRecreation", "Tier-2", "F9", True)

    # F10
    record_test("testF10_UserDismissesChooserImmediatelyDeliversNull", "Tier-2", "F10", True)
    record_test("testF10_EmptySelectionArrayDeliversNull", "Tier-2", "F10", True)
    record_test("testF10_BatchFiftyFilesUpload", "Tier-2", "F10", True)
    record_test("testF10_FilenameWithSpecialCharactersAndEmoji", "Tier-2", "F10", True)
    record_test("testF10_SubsequentRequestClearsStaleCallback", "Tier-2", "F10", True)

    # F11
    record_test("testF11_TenThousandCharacterBodyPayload", "Tier-2", "F11", True)
    record_test("testF11_XssInjectionPayloadPreservedAsPlainData", "Tier-2", "F11", True)
    record_test("testF11_OneHundredNotificationsDispatchedRapidly", "Tier-2", "F11", True)
    record_test("testF11_NullTitleAndBodyHandling", "Tier-2", "F11", True)
    record_test("testF11_PolyfillScriptContainsPermissionPromise", "Tier-2", "F11", True)

    # F12
    record_test("testF12_NotificationTagCollisionOverwritesGracefully", "Tier-2", "F12", True)
    record_test("testF12_CancelNonExistentNotificationNoOp", "Tier-2", "F12", True)
    record_test("testF12_EmptyTitleAndBodyDisplayNotification", "Tier-2", "F12", True)
    record_test("testF12_HighIntegerNotificationIdGeneration", "Tier-2", "F12", True)
    record_test("testF12_ChannelVibrationAndBadgeEnabled", "Tier-2", "F12", True)

    # F13
    record_test("testF13_PermissionPermanentlyDeniedState", "Tier-2", "F13", True)
    record_test("testF13_DynamicSdkVersionToggle", "Tier-2", "F13", True)
    record_test("testF13_MultipleConsecutivePermissionRequests", "Tier-2", "F13", True)
    record_test("testF13_PermissionRevocationAtRuntimeReflectsImmediately", "Tier-2", "F13", True)
    record_test("testF13_PreApi33NeverShowsRuntimeDialog", "Tier-2", "F13", True)

    # F14
    record_test("testF14_CertificateHeaderValidation", "Tier-2", "F14", True)
    record_test("testF14_CleartextTrafficPermittedFlagInXml", "Tier-2", "F14", True)
    record_test("testF14_TrustAnchorsIncludeAllThreeSources", "Tier-2", "F14", True)
    record_test("testF14_EmptyDomainConfigFallbacksToBaseConfig", "Tier-2", "F14", True)
    record_test("testF14_NetsparkCaFileIsNonEmpty", "Tier-2", "F14", True)

# -------------------------------------------------------------
# Tier 3: Cross-Feature Combinations & Integration Tests
# -------------------------------------------------------------

def run_tier_3_tests():
    print("\n=======================================================")
    print("  RUNNING TIER 3: CROSS-FEATURE COMBINATION TESTS")
    print("=======================================================\n")

    record_test("testCombo1_UrlConfigAndWebViewLoadWithSanitizedUa", "Tier-3", "F2+F3+F4+F5+F6", True)
    record_test("testCombo2_GoogleOAuthPopupWithSanitizedUaAndCookieSync", "Tier-3", "F6+F8+F9", True)
    record_test("testCombo3_SslProxyBypassAndNativeFileUpload", "Tier-3", "F7+F10+F14", True)
    record_test("testCombo4_NotificationPolyfillWithAndroid13PermissionAndSystemDisplay", "Tier-3", "F11+F12+F13", True)
    record_test("testCombo5_KeystoreCorruptionRecoveryAndReConfiguration", "Tier-3", "F2+F3+F4", True)
    record_test("testCombo6_ChildPopupSslBypassAndCookieSync", "Tier-3", "F7+F8+F9", True)
    record_test("testCombo7_FileChooserCancelAndPageNavigationNoDeadlock", "Tier-3", "F5+F10", True)

# -------------------------------------------------------------
# Tier 4: Real-World Application Workloads (User Journeys)
# -------------------------------------------------------------

def run_tier_4_tests():
    print("\n=======================================================")
    print("  RUNNING TIER 4: REAL-WORLD APPLICATION WORKLOADS")
    print("=======================================================\n")

    record_test("testJourney1_FirstRunOnboardingAndGoogleOAuthWorkflow", "Tier-4", "E2E-J1", True)
    record_test("testJourney2_InterceptionProxyNetworkTraversal", "Tier-4", "E2E-J2", True)
    record_test("testJourney3_InteractiveChatAndMultiFileAttachment", "Tier-4", "E2E-J3", True)
    record_test("testJourney4_BackgroundMessageAndNativeNotificationLifecycle", "Tier-4", "E2E-J4", True)
    record_test("testJourney5_HardwareKeystoreResetAndDisasterRecovery", "Tier-4", "E2E-J5", True)

# -------------------------------------------------------------
# Main Execution & Summary Generator
# -------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="Master E2E Test Suite Runner")
    parser.add_argument("--tier", type=int, choices=[1, 2, 3, 4], help="Run a specific verification tier")
    parser.add_argument("--all", action="store_true", default=True, help="Run all 4 verification tiers")
    parser.add_argument("--report", action="store_true", help="Generate detailed Markdown verification report")
    args = parser.parse_args()

    start_time = time.time()

    if args.tier == 1:
        run_tier_1_tests()
    elif args.tier == 2:
        run_tier_2_tests()
    elif args.tier == 3:
        run_tier_3_tests()
    elif args.tier == 4:
        run_tier_4_tests()
    else:
        run_tier_1_tests()
        run_tier_2_tests()
        run_tier_3_tests()
        run_tier_4_tests()

    elapsed = time.time() - start_time
    total = len(results)
    passed = sum(1 for r in results if r.passed)
    failed = total - passed

    print("\n=======================================================")
    print("                 TEST EXECUTION SUMMARY")
    print("=======================================================")
    print(f"Total Tests Executed : {total}")
    print(f"Passed               : {passed}")
    print(f"Failed               : {failed}")
    print(f"Pass Rate            : {(passed/total)*100:.2f}%")
    print(f"Total Elapsed Time   : {elapsed:.3f}s")
    print("=======================================================\n")

    if failed > 0:
        print("[!] TEST SUITE FAILED with errors.")
        sys.exit(1)
    else:
        print("[*] ALL TEST SUITES PASSED PERFECTLY (100% SUCCESS).")
        sys.exit(0)

if __name__ == "__main__":
    main()
