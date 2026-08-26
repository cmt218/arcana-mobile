import SwiftUI
import UIKit
import ComposeApp

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
    private(set) var profileVC: UIViewController?
    private(set) var authVC: UIViewController?
    private(set) var splashVC: UIViewController? = SplashHostKt.SplashViewController()

    private var perTabAtRoot: [Int: Bool] = [0: true, 1: true, 2: true]
    private var splashTimerStarted = false

    init() {
        // Pre-Liquid-Glass fallback (iOS 18.x): UIKit picks the tab bar's
        // transparent "scroll edge" appearance whenever it can't observe a
        // UIScrollView scrolling underneath — and Compose content never is
        // one — leaving the items floating over content with no backdrop.
        // Pin BOTH appearances to the system's default (blurred) background
        // so the legacy bar always has proper contrast. iOS 26's Liquid Glass
        // bar draws its own material and must not be touched.
        if #unavailable(iOS 26.0) {
            let appearance = UITabBarAppearance()
            appearance.configureWithDefaultBackground()
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
    }

    /// Anchored to the splash view's first appearance (not model init) so the
    /// minimum display matches the Compose dance/settle choreography exactly —
    /// init runs before the first frame, which would shave the cold-start
    /// first-frame latency off the brand moment.
    func splashDidAppear() {
        guard !splashTimerStarted else { return }
        splashTimerStarted = true
        let ms = IosShellBridge.shared.splashMinDisplayMs()
        DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(Int(ms))) { [weak self] in
            withAnimation(.easeOut(duration: 0.3)) { self?.splashVisible = false }
            // Release the Compose splash scene once the fade completes — the
            // shell never shows it again.
            DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(350)) {
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
        perTabAtRoot = [0: true, 1: true, 2: true]
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
            profileVC = TabRootsKt.ProfileTabViewController { [weak self] atRoot in
                self?.rootChanged(tab: 2, atRoot: atRoot.boolValue)
            }
        } else {
            homeVC = nil; scheduleVC = nil; profileVC = nil
            authVC = AuthFlowRootKt.AuthFlowViewController()
        }
    }

    private func rootChanged(tab: Int, atRoot: Bool) {
        DispatchQueue.main.async {
            self.perTabAtRoot[tab] = atRoot
            self.refreshTabBarVisibility()
        }
    }

    func tabSelected(from previous: Int, to tab: Int) {
        let names = ["home", "schedule", "profile"]
        IosShellBridge.shared.tabSelected(tab: names[tab], fromTab: names[previous])
        // $screen for the newly shown tab root — only on a real switch
        // (same-tab re-taps never re-fired $screen pre-shell either).
        if previous != tab {
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
                    Tab(value: 2) {
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

            if shell.splashVisible, let splash = shell.splashVC {
                ComposeVC(vc: splash)
                    .ignoresSafeArea()
                    .transition(.opacity)
                    .onAppear { shell.splashDidAppear() }
            }
        }
    }
}
