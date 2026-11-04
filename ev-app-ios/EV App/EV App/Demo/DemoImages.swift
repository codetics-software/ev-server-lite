//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

#if DEBUG
import UIKit

enum DemoImages {
    static func station() -> UIImage {
        let size = CGSize(width: 800, height: 600)
        return UIGraphicsImageRenderer(size: size).image { context in
            let colors = [UIColor(red: 0.62, green: 0.8, blue: 0.95, alpha: 1).cgColor, UIColor(red: 0.88, green: 0.94, blue: 0.98, alpha: 1).cgColor]
            let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors as CFArray, locations: [0, 1])!
            context.cgContext.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: 0, y: 420), options: [])
            UIColor(white: 0.45, alpha: 1).setFill()
            context.fill(CGRect(x: 0, y: 420, width: 800, height: 180))
            UIColor(red: 0, green: 0.51, blue: 0.21, alpha: 1).setFill()
            UIBezierPath(roundedRect: CGRect(x: 330, y: 130, width: 140, height: 340), cornerRadius: 22).fill()
            UIColor(white: 0.1, alpha: 1).setFill()
            UIBezierPath(roundedRect: CGRect(x: 350, y: 160, width: 100, height: 70), cornerRadius: 8).fill()
            let bolt = UIImage(systemName: "bolt.fill")?.withTintColor(.white, renderingMode: .alwaysOriginal)
            bolt?.draw(in: CGRect(x: 375, y: 270, width: 50, height: 70))
        }
    }
}
#endif
