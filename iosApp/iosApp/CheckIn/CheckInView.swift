import SwiftUI
import MapKit
import SharedLogic

/// "On your way": the in-store draft, how far the store is, its live queue and Start Cooking, which stays
/// off until the customer is in range. "Scan QR" is the fallback when GPS fails. The panel is a system sheet
/// over the map (glass on iOS 26): it can't be swiped away, and the map stays usable behind it.
struct CheckInView: View {
    @ObservedObject var model: CheckInModel
    let onBack: () -> Void
    let onScanQr: () -> Void
    let onCheckedIn: () -> Void
    /// The sheet belongs to this screen: it goes away when the scanner or seat check is pushed, and returns on Back.
    @State private var panelShown = false
    /// Opened on top of the panel, not from the screen: iOS shows one sheet at a time, so a second sheet from
    /// the screen would dismiss the panel and leave no way to Start Cooking once it closed.
    @State private var showDebugPanel = false

    private var panelVisible: Bool { panelShown && model.state.draft != nil && !model.state.isLoading }

    var body: some View {
        let state = model.state
        ZStack(alignment: .bottom) {
            if state.isLoading {
                ProgressView("Loading your order…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let draft = state.draft {
                if let store = state.store {
                    ArrivalMapView(storeName: draft.storeName, store: store.location, customer: state.customerLocation)
                        .ignoresSafeArea()
                } else {
                    Palette.screen.ignoresSafeArea()
                }
            } else {
                NothingToCheckIn(onBack: onBack)
            }
        }
        .background(Palette.screen)
        .onAppear { panelShown = true }
        .onDisappear { panelShown = false }
        .sheet(isPresented: Binding(get: { panelVisible }, set: { panelShown = $0 })) {
            CheckInPanel(
                state: model.state,
                onStartCooking: { if model.viewModel.startCooking() { onCheckedIn() } },
                onScanQr: onScanQr
            )
            .sheet(isPresented: $showDebugPanel) { DebugPanelView() }
        }
        // Loading or nothing to check in: no panel, so the debug panel opens from the screen.
        .sheet(isPresented: Binding(get: { showDebugPanel && !panelVisible }, set: { showDebugPanel = $0 })) {
            DebugPanelView()
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(action: onBack) {
                    Label("Back", systemImage: "chevron.left")
                }
            }
            if state.draft != nil {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onScanQr) {
                        Label("Scan QR", systemImage: "qrcode")
                            .labelStyle(.titleAndIcon)
                    }
                }
            }
            if AppBuild.isDebug {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showDebugPanel = true } label: {
                        Label("Debug panel", systemImage: "ladybug")
                    }
                }
            }
        }
    }
}

private struct CheckInPanel: View {
    let state: CheckInUiState
    let onStartCooking: () -> Void
    let onScanQr: () -> Void
    @ScaledMetric(relativeTo: .title2) private var headlineSize: CGFloat = 24
    /// The content's height, so the sheet opens exactly that tall and leaves the map usable above it.
    @State private var contentHeight: CGFloat = 420

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 12) {
                        Text(state.headline)
                            .font(.system(size: headlineSize, weight: .bold))
                            .accessibilityAddTraits(.isHeader)
                        Spacer(minLength: 0)
                        ArrivalBadge(state: state)
                    }
                    Text(state.storeLine)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                SummaryCard(rows: rows)

                Button(action: onStartCooking) { CapsuleLabel(title: Copy.startCooking) }
                    .buttonStyle(CapsuleButtonStyle())
                    .disabled(!state.canStartCooking)
                    .accessibilityHint(Copy.titleCased(state.startCookingHint))

                Text(Copy.titleCased(state.startCookingHint))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.top, -4)
                    .accessibilityHidden(true)

                Button(action: onScanQr) {
                    Label("Can't check in? Scan the store QR", systemImage: "qrcode")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Palette.link)
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 22)
            .padding(.bottom, 16)
            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { contentHeight = $0 }
        }
        .scrollBounceBehavior(.basedOnSize)
        .presentationDetents([.height(contentHeight), .large])
        .presentationBackgroundInteraction(.enabled(upThrough: .height(contentHeight)))
        .presentationDragIndicator(.visible)
        // Check-in is the screen's whole point, so the panel stays until the customer goes Back or scans.
        .interactiveDismissDisabled()
    }

    private var rows: [(String, String)] {
        var rows = [
            ("Your order", state.orderSummary),
            ("Distance", state.distanceLabel),
            ("Queue", state.queueLabel),
        ]
        if let checkIn = state.checkIn {
            rows.append(("Check-in", checkIn.label))
        }
        return rows
    }
}

/// The design's grouped white rows with inset dividers.
private struct SummaryCard: View {
    let rows: [(String, String)]

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                HStack {
                    Text(row.0).foregroundStyle(.secondary)
                    Spacer()
                    Text(row.1).fontWeight(.semibold)
                }
                .font(.subheadline)
                .frame(minHeight: 46)
                .padding(.horizontal, 16)
                .accessibilityElement(children: .combine)
                if index < rows.count - 1 {
                    Divider().padding(.leading, 16)
                }
            }
        }
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

private struct ArrivalBadge: View {
    let state: CheckInUiState

    var body: some View {
        let colors = state.hasArrived ? Palette.badge(.openForLunch) : Palette.badge(.paused)
        Label(text, systemImage: state.hasArrived ? "checkmark" : "location")
            .font(.footnote.weight(.semibold))
            .lineLimit(1)
            .padding(.horizontal, 10)
            .frame(minHeight: 26)
            .foregroundStyle(colors.foreground)
            .background(colors.background, in: Capsule())
            .animation(.easeInOut, value: state.hasArrived)
    }

    private var text: String {
        if state.checkIn != nil { return "Checked in" }
        return state.isInRange ? "In range" : "Not in range"
    }
}

private struct NothingToCheckIn: View {
    let onBack: () -> Void

    var body: some View {
        ContentUnavailableView {
            Label("Nothing to check in", systemImage: "fork.knife")
        } description: {
            Text("This order isn't waiting for check-in any more.")
        } actions: {
            Button("Go back", action: onBack)
                .buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Apple Maps with the store, its geofence and the customer's dot, which moves inside the fence on arrival.
/// The mockup never reads the real location: the dot is the mock position the debug panel moves.
/// Points of interest are hidden, so real businesses don't sit beside the sample stores.
/// Decorative: the panel says the same in words, so VoiceOver skips it.
private struct ArrivalMapView: View {
    let storeName: String
    let store: GeoPoint
    let customer: GeoPoint?

    @State private var position: MapCameraPosition = .automatic

    private let you = Color(red: 0.04, green: 0.52, blue: 1.0)

    var body: some View {
        Map(position: $position, interactionModes: [.pan, .zoom]) {
            MapCircle(center: store.coordinate, radius: CLLocationDistance(Geofence.shared.RADIUS_METERS))
                .foregroundStyle(Palette.action.opacity(0.08))
                .stroke(Palette.action.opacity(0.45), style: StrokeStyle(lineWidth: 2, dash: [6, 6]))
            Annotation(storeName, coordinate: store.coordinate) {
                Image(systemName: "fork.knife")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 48, height: 48)
                    .background(Palette.action, in: Circle())
                    .overlay(Circle().strokeBorder(.white, lineWidth: 3))
                    .shadow(color: .black.opacity(0.25), radius: 8, y: 6)
                    // The artboard's white name pill sits above the pin.
                    .overlay(alignment: .top) {
                        Text(storeName)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.black)
                            .lineLimit(1)
                            .fixedSize()
                            .padding(.horizontal, 10)
                            .frame(height: 26)
                            .background(.white, in: Capsule())
                            .shadow(color: .black.opacity(0.15), radius: 4, y: 2)
                            .offset(y: -32)
                    }
            }
            .annotationTitles(.hidden)
            if let customer {
                Annotation("You", coordinate: customer.coordinate) {
                    ZStack {
                        Circle().fill(you.opacity(0.16)).frame(width: 48, height: 48)
                        Circle().fill(.white).frame(width: 22, height: 22)
                        Circle().fill(you).frame(width: 16, height: 16)
                    }
                }
                .annotationTitles(.hidden)
            }
        }
        .mapStyle(.standard(pointsOfInterest: .excludingAll))
        .onAppear { position = camera() }
        .onChange(of: customerKey) {
            withAnimation(.easeInOut(duration: 0.6)) { position = camera() }
        }
        .accessibilityHidden(true)
    }

    /// Changes whenever the customer moves, since GeoPoint isn't Equatable in Swift.
    private var customerKey: String {
        customer.map { "\($0.latitude),\($0.longitude)" } ?? ""
    }

    /// Frames the store, its geofence and the customer in the top part of the screen, above the panel.
    private func camera() -> MapCameraPosition {
        let points = [store] + (customer.map { [$0] } ?? [])
        let latitudes = points.map(\.latitude)
        let longitudes = points.map(\.longitude)
        let fence = Double(Geofence.shared.RADIUS_METERS) * 2 / 111_320
        let latitudeSpan = (latitudes.max()! - latitudes.min()!) + fence * 2
        let longitudeSpan = (longitudes.max()! - longitudes.min()!) + fence * 2 / cos(store.latitude * .pi / 180)
        let center = CLLocationCoordinate2D(
            latitude: (latitudes.max()! + latitudes.min()!) / 2 - latitudeSpan * 0.6,
            longitude: (longitudes.max()! + longitudes.min()!) / 2
        )
        return .region(MKCoordinateRegion(
            center: center,
            span: MKCoordinateSpan(latitudeDelta: latitudeSpan * 2.4, longitudeDelta: longitudeSpan * 1.3)
        ))
    }
}

private extension GeoPoint {
    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}
