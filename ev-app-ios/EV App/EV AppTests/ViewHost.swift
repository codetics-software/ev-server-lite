//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI
import Testing
import UIKit
@testable import EV_App

@MainActor
final class ViewHost {
    private let window: UIWindow

    private static let automation: Void = {
        guard let handle = dlopen("/usr/lib/libAccessibility.dylib", RTLD_NOW), let symbol = dlsym(handle, "_AXSSetAutomationEnabled") else { return }
        typealias Setter = @convention(c) (Int32) -> Void
        unsafeBitCast(symbol, to: Setter.self)(1)
    }()

    init(_ view: some View, model: AppModel = .shared) {
        Self.automation
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        window = scene.map { UIWindow(windowScene: $0) } ?? UIWindow()
        window.frame = CGRect(x: 0, y: 0, width: 402, height: 2_400)
        window.rootViewController = UIHostingController(rootView: view.environment(model))
        window.windowLevel = .normal + 1
        window.isHidden = false
        window.layoutIfNeeded()
    }

    func close() {
        window.isHidden = true
        window.rootViewController = nil
    }

    var texts: [String] {
        var found: [String] = []
        collect(window, into: &found, depth: 0)
        return found
    }

    func contains(_ text: String) -> Bool {
        texts.contains { $0.localizedStandardContains(text) }
    }

    @discardableResult
    func waitFor(_ text: String, timeout: Duration = .seconds(10)) async -> Bool {
        await waitUntil(timeout: timeout) { self.contains(text) }
    }

    @discardableResult
    func waitUntil(timeout: Duration = .seconds(10), _ condition: () -> Bool) async -> Bool {
        let deadline = ContinuousClock.now + timeout
        while ContinuousClock.now < deadline {
            window.layoutIfNeeded()
            if condition() { return true }
            try? await Task.sleep(for: .milliseconds(50))
        }
        return condition()
    }

    func settle(_ duration: Duration = .milliseconds(400)) async {
        let deadline = ContinuousClock.now + duration
        while ContinuousClock.now < deadline {
            window.layoutIfNeeded()
            try? await Task.sleep(for: .milliseconds(50))
        }
    }

    func element(identifier: String) -> NSObject? {
        find(window, depth: 0) { Self.identifier(of: $0) == identifier }
    }

    func element(labelPrefix: String) -> NSObject? {
        find(window, depth: 0) { $0.accessibilityLabel?.hasPrefix(labelPrefix) == true }
    }

    @discardableResult
    func activate(labelPrefix: String) -> Bool {
        element(labelPrefix: labelPrefix)?.accessibilityActivate() ?? false
    }

    private static func identifier(of object: NSObject) -> String? {
        if let identified = object as? UIAccessibilityIdentification { return identified.accessibilityIdentifier }
        guard object.responds(to: NSSelectorFromString("accessibilityIdentifier")) else { return nil }
        return object.value(forKey: "accessibilityIdentifier") as? String
    }

    func element(label: String) -> NSObject? {
        find(window, depth: 0) { $0.accessibilityLabel == label }
    }

    @discardableResult
    func activate(identifier: String) -> Bool {
        press(element(identifier: identifier))
    }

    @discardableResult
    func activate(label: String) -> Bool {
        if press(element(label: label)) { return true }
        var matches: [NSObject] = []
        findAll(window, depth: 0, into: &matches) { $0.accessibilityLabel == label }
        for match in matches.reversed() where press(match) { return true }
        return press(barButton(label: label))
    }

    private func findAll(_ object: NSObject, depth: Int, into matches: inout [NSObject], where predicate: (NSObject) -> Bool) {
        guard depth < 80 else { return }
        if predicate(object) { matches.append(object) }
        for child in children(of: object) {
            findAll(child, depth: depth + 1, into: &matches, where: predicate)
        }
    }

    @discardableResult
    func activate(containing text: String) -> Bool {
        press(find(window, depth: 0) { $0.accessibilityLabel?.contains(text) == true })
    }

    private func press(_ element: NSObject?) -> Bool {
        guard let element else { return false }
        if element.accessibilityActivate() { return true }
        if let control = element as? UIControl {
            control.sendActions(for: .touchUpInside)
            return true
        }
        if let view = element as? UIView, let control = firstControl(in: view) {
            control.sendActions(for: .touchUpInside)
            return true
        }
        return false
    }

    private func firstControl(in view: UIView) -> UIControl? {
        if let control = view as? UIControl { return control }
        for subview in view.subviews {
            if let control = firstControl(in: subview) { return control }
        }
        return nil
    }

    private func barButton(label: String) -> NSObject? {
        var bars: [UINavigationBar] = []
        collectBars(window, into: &bars)
        for bar in bars {
            for item in bar.items ?? [] {
                let buttons = (item.leftBarButtonItems ?? []) + (item.rightBarButtonItems ?? [])
                for button in buttons where button.title == label || button.accessibilityLabel == label {
                    if let custom = button.customView { return custom }
                    if let action = button.action {
                        UIApplication.shared.sendAction(action, to: button.target, from: button, for: nil)
                        return BarButtonPressed()
                    }
                }
            }
        }
        return nil
    }

    private func collectBars(_ view: UIView, into bars: inout [UINavigationBar]) {
        if let bar = view as? UINavigationBar { bars.append(bar) }
        view.subviews.forEach { collectBars($0, into: &bars) }
    }

    func textFields() -> [UITextField] {
        var fields: [UITextField] = []
        collectFields(window, into: &fields)
        return fields
    }

    func textField(_ placeholder: String) -> UITextField? {
        textFields().first { $0.placeholder == placeholder }
    }

    @discardableResult
    func type(_ text: String, into placeholder: String) -> Bool {
        guard let field = textField(placeholder) else { return false }
        field.becomeFirstResponder()
        if let range = field.textRange(from: field.beginningOfDocument, to: field.endOfDocument) {
            field.replace(range, withText: text)
        } else {
            field.insertText(text)
        }
        field.sendActions(for: .editingChanged)
        field.resignFirstResponder()
        return true
    }

    private func collectFields(_ view: UIView, into fields: inout [UITextField]) {
        if let field = view as? UITextField { fields.append(field) }
        view.subviews.forEach { collectFields($0, into: &fields) }
    }

    private func children(of object: NSObject) -> [NSObject] {
        var result: [NSObject] = []
        if let elements = object.accessibilityElements as? [NSObject] {
            result.append(contentsOf: elements)
        } else {
            let count = object.accessibilityElementCount()
            if count != NSNotFound, count > 0 {
                for index in 0..<count {
                    if let element = object.accessibilityElement(at: index) as? NSObject { result.append(element) }
                }
            }
        }
        if let view = object as? UIView {
            result.append(contentsOf: view.subviews)
        }
        return result
    }

    private func collect(_ object: NSObject, into found: inout [String], depth: Int) {
        guard depth < 80 else { return }
        if let label = object.accessibilityLabel, !label.isEmpty { found.append(label) }
        if let value = object.accessibilityValue, !value.isEmpty { found.append(value) }
        if let label = object as? UILabel, let text = label.text { found.append(text) }
        for child in children(of: object) {
            collect(child, into: &found, depth: depth + 1)
        }
    }

    private func find(_ object: NSObject, depth: Int, where predicate: (NSObject) -> Bool) -> NSObject? {
        guard depth < 80 else { return nil }
        if predicate(object) { return object }
        for child in children(of: object) {
            if let match = find(child, depth: depth + 1, where: predicate) { return match }
        }
        return nil
    }
}

private final class BarButtonPressed: NSObject {
    override func accessibilityActivate() -> Bool { true }
}

@MainActor
final class Box<Value> {
    var value: Value?
}
