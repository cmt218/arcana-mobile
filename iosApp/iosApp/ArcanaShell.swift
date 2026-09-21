import SwiftUI
import UIKit
import ComposeApp

/// UIKit builds its text-input system inside the first tap on any field and
/// blocks the main thread while it does (measured: docs/perf/README.md, "First
/// keyboard focus"). Pay that once under the splash instead.
enum KeyboardPrewarm {
    private static var done = false

    static func run() {
        guard !done,
              let window = UIApplication.shared.connectedScenes
                  .compactMap({ $0 as? UIWindowScene })
                  .flatMap(\.windows)
                  .first(where: \.isKeyWindow)
        else { return }
        done = true
        let field = UITextField(frame: .zero)
        field.alpha = 0
        // An empty input view: UIKit still builds its text-input system, but is
        // never asked to present the system keyboard, so nothing can flash.
        field.inputView = UIView(frame: .zero)
        field.inputAssistantItem.leadingBarButtonGroups = []
        field.inputAssistantItem.trailingBarButtonGroups = []
        window.addSubview(field)
        UIView.performWithoutAnimation {
            field.becomeFirstResponder()
            field.resignFirstResponder()
        }
        field.removeFromSuperview()
    }
}

/// The SwiftUI Liquid Glass shell: native TabView (system Liquid Glass tab
/// bar on iOS 26) hosting per-tab Compose content, with the auth flow and the
/// dot-matrix splash as Compose controllers. Kotlin drives session state via
/// IosShellBridge; all business logic stays in the shared Kotlin core.
final class ShellModel: ObservableObject {
    @Published var isAuthenticated: Bool
    @Published var splashVisible = true
    @Published var selectedTab = 0
    @Published var tabBarHidden = false
    @Published var memberInitials: String?

    private(set) var homeVC: UIViewController?
    private(set) var scheduleVC: UIViewController?
    private(set) var discoverVC: UIViewController?
    private(set) var profileVC: UIViewController?
    private(set) var authVC: UIViewController?
    private(set) var splashVC: UIViewController? = SplashHostKt.SplashViewController()

    private var perTabAtRoot: [Int: Bool] = [0: true, 1: true, 2: true, 3: true]
    private var splashTimerStarted = false
    // The native TabView never calls the Compose TabBar's Kotlin selection
    // haptic, so the shell fires system selection feedback on a real switch.
    private let selectionHaptic = UISelectionFeedbackGenerator()

    init() {
        // Pre-Liquid-Glass fallback (iOS 18.x): UIKit picks the tab bar's
        // transparent "scroll edge" appearance whenever it can't observe a
        // UIScrollView scrolling underneath — and Compose content never is one.
        // The default blurred material then blooms as a white halo over the
        // Book tab's lime atmosphere; pin an OPAQUE Stone background on both
        // appearances so the bar matches the app and reads cleanly. iOS 26's
        // Liquid Glass bar draws its own material and must not be touched.
        if #unavailable(iOS 26.0) {
            let appearance = UITabBarAppearance()
            appearance.configureWithOpaqueBackground()
            appearance.backgroundColor = UIColor(
                red: 0xF5 / 255.0, green: 0xF2 / 255.0, blue: 0xED / 255.0, alpha: 1
            )
            UITabBar.appearance().standardAppearance = appearance
            UITabBar.appearance().scrollEdgeAppearance = appearance
        }

        // PostHog + Sentry first so crash capture is armed before Kotlin runs.
        let telemetry = TelemetryBootstrap.start()
        IosShellBridge.shared.start(
            analytics: telemetry.analytics,
            crashReporter: telemetry.crashReporter
        )
        isAuthenticated = IosShellBridge.shared.isAuthenticated()
        buildControllers(authenticated: isAuthenticated)

        IosShellBridge.shared.observeAuthentication { [weak self] authed in
            DispatchQueue.main.async { self?.authChanged(authed.boolValue) }
        }
        // Member initials for the You-tab avatar chip (Moss circle + Stone
        // initials — the Compose bar's brand affordance, kept on the shell).
        IosShellBridge.shared.observeMemberInitials { [weak self] initials in
            DispatchQueue.main.async { self?.memberInitials = initials }
        }
        IosShellBridge.shared.onTabRequested = { [weak self] name in
            DispatchQueue.main.async { self?.selectTab(named: name) }
        }
    }

    /// Anchored to the splash view's first appearance (not model init) so the
    /// minimum display matches the Compose dance/settle choreography exactly —
    /// init runs before the first frame, which would shave the cold-start
    /// first-frame latency off the brand moment.
    func splashDidAppear() {
        guard !splashTimerStarted else { return }
        splashTimerStarted = true
        // Before the timer, so the splash's minimum is measured after the stall.
        KeyboardPrewarm.run()
        let ms = IosShellBridge.shared.splashMinDisplayMs()
        let exitMs = Int(IosShellBridge.shared.splashExitMs())
        DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(Int(ms))) { [weak self] in
            withAnimation(.easeInOut(duration: Double(exitMs) / 1000)) { self?.splashVisible = false }
            // Release the Compose splash scene once the exit completes — the
            // shell never shows it again.
            DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(exitMs + 50)) {
                self?.splashVC = nil
            }
        }
    }

    private func authChanged(_ authed: Bool) {
        guard authed != isAuthenticated else { return }
        // Deterministically clear every retiring controller's ViewModelStore
        // (cancelling viewModelScopes) — the pre-shell sessionStore.clear()
        // semantic — then rebuild fresh controllers for the new session.
        IosShellBridge.shared.clearSessionViewModelStores()
        buildControllers(authenticated: authed)
        isAuthenticated = authed
        selectedTab = 0
        perTabAtRoot = [0: true, 1: true, 2: true, 3: true]
        tabBarHidden = false
    }

    private func buildControllers(authenticated: Bool) {
        if authenticated {
            authVC = nil
            homeVC = TabRootsKt.HomeTabViewController { [weak self] atRoot in
                self?.rootChanged(tab: 0, atRoot: atRoot.boolValue)
            }
            scheduleVC = TabRootsKt.ScheduleTabViewController { [weak self] atRoot in
                self?.rootChanged(tab: 1, atRoot: atRoot.boolValue)
            }
            discoverVC = TabRootsKt.DiscoverTabViewController { [weak self] atRoot in
                self?.rootChanged(tab: 2, atRoot: atRoot.boolValue)
            }
            profileVC = TabRootsKt.ProfileTabViewController { [weak self] atRoot in
                self?.rootChanged(tab: 3, atRoot: atRoot.boolValue)
            }
        } else {
            homeVC = nil; scheduleVC = nil; discoverVC = nil; profileVC = nil
            authVC = AuthFlowRootKt.AuthFlowViewController()
        }
    }

    private func rootChanged(tab: Int, atRoot: Bool) {
        DispatchQueue.main.async {
            self.perTabAtRoot[tab] = atRoot
            self.refreshTabBarVisibility()
        }
    }

    /// Compose content asking for a tab (the Reservations "Book a class"
    /// action). Runs the same bookkeeping as a bar tap: telemetry, haptic,
    /// $screen and bar visibility.
    func selectTab(named name: String) {
        let names = ["home", "schedule", "discover", "profile"]
        guard let tab = names.firstIndex(of: name), tab != selectedTab else { return }
        let previous = selectedTab
        selectedTab = tab
        tabSelected(from: previous, to: tab)
    }

    func tabSelected(from previous: Int, to tab: Int) {
        let names = ["home", "schedule", "discover", "profile"]
        IosShellBridge.shared.tabSelected(tab: names[tab], fromTab: names[previous])
        // $screen for the newly shown tab root — only on a real switch
        // (same-tab re-taps never re-fired $screen pre-shell either).
        if previous != tab {
            selectionHaptic.selectionChanged()
            selectionHaptic.prepare()
            IosShellBridge.shared.tabRootShown(tab: names[tab])
        }
        refreshTabBarVisibility()
    }

    private func refreshTabBarVisibility() {
        tabBarHidden = !(perTabAtRoot[selectedTab] ?? true)
    }
}

/// Renders the member-initials avatar chip (Moss circle, Stone initials) as a
/// tab-bar-sized image — the same brand affordance the Compose ArcanaTabBar
/// draws on Android. Rendered `.original` so the bar doesn't template-tint it;
/// selection reads through the glass capsule highlight and label tint.
enum AvatarChip {
    static var cache: [String: UIImage] = [:]

    static func image(for initials: String) -> UIImage {
        if let cached = cache[initials] { return cached }
        let size = CGSize(width: 26, height: 26)
        let renderer = UIGraphicsImageRenderer(size: size)
        let img = renderer.image { ctx in
            let moss = UIColor(red: 0x28 / 255.0, green: 0x3B / 255.0, blue: 0x15 / 255.0, alpha: 1)
            let stone = UIColor(red: 0xF5 / 255.0, green: 0xF2 / 255.0, blue: 0xED / 255.0, alpha: 1)
            moss.setFill()
            ctx.cgContext.fillEllipse(in: CGRect(origin: .zero, size: size))
            let font = UIFont.systemFont(ofSize: 11, weight: .bold)
            let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: stone]
            let text = NSAttributedString(string: initials, attributes: attrs)
            let textSize = text.size()
            text.draw(at: CGPoint(x: (size.width - textSize.width) / 2,
                                  y: (size.height - textSize.height) / 2))
        }
        // .alwaysOriginal must be baked into the UIImage itself — the tab bar
        // template-tints icon images regardless of SwiftUI's .renderingMode.
        let original = img.withRenderingMode(.alwaysOriginal)
        cache[initials] = original
        return original
    }
}

struct ComposeVC: UIViewControllerRepresentable {
    let vc: UIViewController
    func makeUIViewController(context: Context) -> UIViewController { vc }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ArcanaShellView: View {
    @EnvironmentObject var shell: ShellModel

    // Brand Moss for the selected tab item on the glass bar. The app locks
    // light appearance (UIUserInterfaceStyle in Info.plist) — the Stone-light
    // design has no dark theme, and Moss-on-dark-glass would fail contrast.
    private let moss = Color(red: 0x28 / 255.0, green: 0x3B / 255.0, blue: 0x15 / 255.0)

    var body: some View {
        ZStack {
            if shell.isAuthenticated {
                TabView(selection: Binding(
                    get: { shell.selectedTab },
                    set: { newTab in
                        let previous = shell.selectedTab
                        shell.selectedTab = newTab
                        shell.tabSelected(from: previous, to: newTab)
                    }
                )) {
                    Tab("Home", systemImage: "house", value: 0) {
                        if let vc = shell.homeVC {
                            ComposeVC(vc: vc)
                                .ignoresSafeArea()
                                .toolbar(shell.tabBarHidden ? .hidden : .visible, for: .tabBar)
                        }
                    }
                    Tab("Book", systemImage: "calendar", value: 1) {
                        if let vc = shell.scheduleVC {
                            ComposeVC(vc: vc)
                                .ignoresSafeArea()
                                .toolbar(shell.tabBarHidden ? .hidden : .visible, for: .tabBar)
                        }
                    }
                    Tab("Discover", systemImage: "safari", value: 2) {
                        if let vc = shell.discoverVC {
                            ComposeVC(vc: vc)
                                .ignoresSafeArea()
                                .toolbar(shell.tabBarHidden ? .hidden : .visible, for: .tabBar)
                        }
                    }
                    Tab(value: 3) {
                        if let vc = shell.profileVC {
                            ComposeVC(vc: vc)
                                .ignoresSafeArea()
                                .toolbar(shell.tabBarHidden ? .hidden : .visible, for: .tabBar)
                        }
                    } label: {
                        if let initials = shell.memberInitials {
                            Label {
                                Text("You")
                            } icon: {
                                Image(uiImage: AvatarChip.image(for: initials))
                                    .renderingMode(.original)
                            }
                        } else {
                            Label("You", systemImage: "person.crop.circle")
                        }
                    }
                }
                .tint(moss)
            } else if let vc = shell.authVC {
                ComposeVC(vc: vc).ignoresSafeArea()
            }

            // The live splash fades in place and is dropped after (splashVC = nil). As a
            // removal transition in this ZStack it animated out behind the tabs: a one-frame cut.
            if let splash = shell.splashVC {
                ComposeVC(vc: splash)
                    .ignoresSafeArea()
                    .opacity(shell.splashVisible ? 1 : 0)
                    .allowsHitTesting(shell.splashVisible)
                    .zIndex(1)
                    .onAppear { shell.splashDidAppear() }
            }
        }
    }
}
