package com.antigravity.remotecontrol.e2e

import com.antigravity.remotecontrol.build.GradleScaffoldingTest
import com.antigravity.remotecontrol.nativebridge.NativeFileChooserTest
import com.antigravity.remotecontrol.nativebridge.NotificationBridgeTest
import com.antigravity.remotecontrol.nativebridge.NotificationManagerTest
import com.antigravity.remotecontrol.nativebridge.PermissionLifecycleTest
import com.antigravity.remotecontrol.security.NetworkSecurityConfigTest
import com.antigravity.remotecontrol.security.SecurePreferencesManagerTest
import com.antigravity.remotecontrol.security.UrlValidatorTest
import com.antigravity.remotecontrol.ui.UrlConfigDialogTest
import com.antigravity.remotecontrol.web.AuthPopupWindowTest
import com.antigravity.remotecontrol.web.CookiePersistenceTest
import com.antigravity.remotecontrol.web.SslBypassTest
import com.antigravity.remotecontrol.web.UserAgentSanitizerTest
import com.antigravity.remotecontrol.web.WebEngineManagerTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Master E2E and Unit Test Suite Runner.
 * Executes all 4 tiers of verification across the Antigravity Remote Control Android App.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // Tier 1: Feature Coverage (F1 - F14)
    GradleScaffoldingTest::class,
    SecurePreferencesManagerTest::class,
    UrlValidatorTest::class,
    UrlConfigDialogTest::class,
    WebEngineManagerTest::class,
    UserAgentSanitizerTest::class,
    SslBypassTest::class,
    AuthPopupWindowTest::class,
    CookiePersistenceTest::class,
    NativeFileChooserTest::class,
    NotificationBridgeTest::class,
    NotificationManagerTest::class,
    PermissionLifecycleTest::class,
    NetworkSecurityConfigTest::class,

    // Tier 2: Boundary, Corner Cases & Adversarial Stress
    Tier2BoundaryAndAdversarialTest::class,
    EmpiricalStressChallengerTest::class,


    // Tier 3: Cross-Feature Combinations & Subsystem Interactions
    Tier3CrossFeatureIntegrationTest::class,

    // Tier 4: Real-World Application Workloads & User Journeys
    Tier4RealWorldWorkloadsTest::class
)
class E2ETestSuiteRunner
