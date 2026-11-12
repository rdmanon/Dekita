import Combine
import Foundation
import SharedLogic
import UserNotifications

/// Posts ORDER-01's order notices as local notifications. The mock has no server push.
/// One notification per order, so "ready" replaces "will be ready shortly".
/// Notices arrive while the app runs; iOS suspends the app soon after it goes to the background,
/// and the mock timers pause until it comes back.
final class OrderNotifier: NSObject, ObservableObject, UNUserNotificationCenterDelegate {
    static let shared = OrderNotifier()

    /// The order a tapped notification asked to open. Untrusted: check it with `canOpenTicket` first.
    @Published var openedOrderId: String?

    private let bridge = OrderNoticeBridge()
    private var watches: [FlowWatch] = []
    private var center: UNUserNotificationCenter { .current() }

    /// Call once at launch, before the first scene, so a tap that launches the app is delivered here.
    func start() {
        guard watches.isEmpty else { return }
        center.delegate = self
        watches = [
            bridge.watchNotices { [weak self] notice in self?.post(notice) },
            // A signed-out phone shouldn't keep showing the last customer's orders.
            bridge.watchSignedOut { [weak self] in self?.center.removeAllDeliveredNotifications() },
        ]
    }

    /// Asks only the first time. Called when a ticket opens, where the reason for asking is clear.
    func requestAuthorizationIfNeeded() {
        center.getNotificationSettings { [weak self] settings in
            guard settings.authorizationStatus == .notDetermined else { return }
            self?.center.requestAuthorization(options: [.alert, .sound]) { _, _ in }
        }
    }

    /// A paid order while signed in. Anything else from a tapped notification is ignored.
    func canOpenTicket(_ orderId: String) -> Bool {
        bridge.canOpenTicket(orderId: orderId)
    }

    private func post(_ notice: OrderNotice) {
        // Without permission the system drops the request; the ticket and Orders still show the status.
        let content = UNMutableNotificationContent()
        content.title = notice.title
        content.body = notice.body
        content.sound = .default
        content.threadIdentifier = notice.orderId
        content.userInfo = ["orderId": notice.orderId]
        center.add(UNNotificationRequest(identifier: "order-\(notice.orderId)", content: content, trigger: nil))
    }

    // Shows the banner while the app is open too, as on Android.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let orderId = response.notification.request.content.userInfo["orderId"] as? String
        DispatchQueue.main.async {
            self.openedOrderId = orderId
            completionHandler()
        }
    }
}
