import SwiftUI
import AVFoundation

/// Garde le lecteur audio en vie après la fin de l'animation (le son dure un peu plus longtemps).
enum IntroSound {
    static var player: AVAudioPlayer?

    static func play() {
        guard let url = Bundle.main.url(forResource: "orbis_intro", withExtension: "m4a") else { return }
        player = try? AVAudioPlayer(contentsOf: url)
        player?.volume = 0.9
        player?.play()
    }
}

/// Animation d'ouverture « orbite » (même chronologie que la version Android) :
/// arc lumineux qui tourne et se referme, onde, satellite en spirale, puis « ORBIS ».
struct SplashView: View {
    let playSound: Bool
    let onFinished: () -> Void

    static let total: Double = 2.6
    @State private var start = Date()

    var body: some View {
        TimelineView(.animation) { timeline in
            let t = timeline.date.timeIntervalSince(start)
            Canvas { ctx, size in
                SplashView.draw(&ctx, size: size, t: t)
            }
        }
        .background(Color.black)
        .ignoresSafeArea()
        .onAppear {
            start = Date()
            if playSound { IntroSound.play() }
            DispatchQueue.main.asyncAfter(deadline: .now() + SplashView.total) { onFinished() }
        }
    }

    private static func clamp(_ x: Double) -> Double { min(max(x, 0), 1) }
    private static func easeOut(_ x: Double) -> Double { 1 - pow(1 - clamp(x), 3) }
    private static func easeInOut(_ x: Double) -> Double {
        let v = clamp(x)
        return v < 0.5 ? 4 * v * v * v : 1 - pow(-2 * v + 2, 3) / 2
    }

    private static func arc(_ c: CGPoint, _ r: CGFloat, from: Double, sweep: Double) -> Path {
        var p = Path()
        p.addArc(center: c, radius: r, startAngle: .degrees(from), endAngle: .degrees(from + sweep), clockwise: false)
        return p
    }

    private static func circle(_ c: CGPoint, _ r: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r))
    }

    static func draw(_ ctx: inout GraphicsContext, size: CGSize, t: Double) {
        let m = min(size.width, size.height)
        let r = 0.12 * m
        let c = CGPoint(x: size.width / 2, y: size.height / 2 - 0.25 * r)
        let sw = 0.18 * r
        let exit = clamp((t - 2.15) / 0.4)
        let ga = 1 - exit

        let sc = 1 + 0.06 * exit
        ctx.translateBy(x: c.x, y: c.y)
        ctx.scaleBy(x: sc, y: sc)
        ctx.translateBy(x: -c.x, y: -c.y)

        // Anneaux d'orbite
        let oa = 0.12 * clamp((t - 0.1) / 0.5) * (1 - clamp((t - 1.5) / 0.6)) * ga
        if oa > 0 {
            for k in [1.9, 2.8] {
                ctx.stroke(circle(c, r * k), with: .color(.white.opacity(oa)), lineWidth: 1)
            }
        }

        // Arc lumineux
        let head = -90 + 540 * easeOut(t / 1.0)
        let length = 20 + 340 * easeInOut((t - 0.1) / 0.9)
        let path = arc(c, r, from: head - length, sweep: length)
        ctx.drawLayer { layer in
            layer.addFilter(.blur(radius: sw * 0.9))
            layer.stroke(path, with: .color(.white.opacity(0.6 * ga)), style: StrokeStyle(lineWidth: sw * 1.6, lineCap: .round))
        }
        ctx.stroke(path, with: .color(.white.opacity(ga)), style: StrokeStyle(lineWidth: sw, lineCap: .round))

        // Onde
        if t >= 1.0 && t <= 1.7 {
            let e = easeOut((t - 1.0) / 0.7)
            let rr = r * (1 + 1.7 * e)
            ctx.drawLayer { layer in
                layer.addFilter(.blur(radius: sw))
                layer.stroke(circle(c, rr), with: .color(.white.opacity(0.35 * (1 - e) * ga)), lineWidth: sw * 2)
            }
            ctx.stroke(circle(c, rr), with: .color(.white.opacity(0.55 * (1 - e) * ga)), lineWidth: sw * (1 - e) + 1)
        }

        // Satellite violet
        let p = easeOut((t - 0.2) / 1.15)
        let da = clamp((t - 0.2) / 0.25) * ga
        if da > 0 {
            let ang = (-45 - 720 * (1 - p)) * .pi / 180
            let dist = 1.16 * r + (2.8 * r - 1.16 * r) * (1 - p)
            let pos = CGPoint(x: c.x + dist * cos(ang), y: c.y + dist * sin(ang))
            let dr = 0.2 * r
            ctx.drawLayer { layer in
                layer.addFilter(.blur(radius: dr * 1.4))
                layer.fill(circle(pos, dr * 1.8), with: .color(Palette.brand.opacity(0.7 * da)))
            }
            ctx.fill(circle(pos, dr), with: .color(Palette.brand.opacity(da)))
        }

        // ORBIS
        let ta = clamp((t - 1.2) / 0.5) * ga
        if ta > 0 {
            let fontSize = 0.46 * r
            let tracking = (0.32 + 0.6 * (1 - easeOut((t - 1.2) / 0.7))) * fontSize
            let text = Text("ORBIS")
                .font(.system(size: fontSize, weight: .regular))
                .tracking(tracking)
                .foregroundColor(.white.opacity(ta))
            ctx.draw(text, at: CGPoint(x: c.x + tracking / 2, y: c.y + 2.1 * r))
        }
    }
}
