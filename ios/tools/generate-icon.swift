// Render the original scoreboard mark as an opaque 1024px App Store icon.
import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

let size = 1024
let context = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8,
    bytesPerRow: size * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!,
    bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
let mint = CGColor(srgbRed: 98/255, green: 190/255, blue: 175/255, alpha: 1)
let white = CGColor(srgbRed: 1, green: 1, blue: 1, alpha: 1)
context.setFillColor(mint)
context.fill(CGRect(x: 0, y: 0, width: size, height: size))
func rounded(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ radius: Double, _ color: CGColor) {
    context.setFillColor(color)
    context.addPath(CGPath(roundedRect: CGRect(x: x*2, y: (512-y-h)*2, width: w*2, height: h*2),
        cornerWidth: radius*2, cornerHeight: radius*2, transform: nil))
    context.fillPath()
}
rounded(121, 168, 270, 176, 32, white)
rounded(150, 197, 67, 118, 25, mint)
rounded(295, 197, 67, 118, 25, mint)
rounded(172, 219, 23, 74, 11.5, white)
rounded(317, 219, 23, 74, 11.5, white)
context.setFillColor(mint)
for y in [231.0, 281.0] {
    context.fillEllipse(in: CGRect(x: 490, y: (512-y-11)*2, width: 44, height: 44))
}
let destination = CGImageDestinationCreateWithURL(URL(fileURLWithPath: CommandLine.arguments[1]) as CFURL,
    UTType.png.identifier as CFString, 1, nil)!
CGImageDestinationAddImage(destination, context.makeImage()!, nil)
guard CGImageDestinationFinalize(destination) else { fatalError("Could not write icon") }
