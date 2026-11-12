import SwiftUI

extension View {
    /// Liquid Glass on iOS 26, for small floating controls such as a sheet's close button. Earlier versions
    /// get the closest system material. Never for cards or backgrounds: sheets already draw their own glass.
    @ViewBuilder
    func glassControl<S: Shape>(in shape: S) -> some View {
        if #available(iOS 26, *) {
            glassEffect(.regular.interactive(), in: shape)
        } else {
            background(.regularMaterial, in: shape)
        }
    }
}
