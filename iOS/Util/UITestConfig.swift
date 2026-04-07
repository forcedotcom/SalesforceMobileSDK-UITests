/*
 * Copyright (c) 2026-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
//
//  UITestConfig.swift
//  SalesforceMobileSDK-UITest
//

import Foundation
import UIKit

private class BundleAnchor {}

struct UITestConfig: Decodable {
    let loginHosts: [LoginHost]

    struct LoginHost: Decodable {
        let name: String
        let url: String
        let users: [User]
    }

    struct User: Decodable {
        let username: String
        let password: String
    }

    static let shared: UITestConfig = {
        guard let url = Bundle(for: BundleAnchor.self).url(forResource: "ui_test_config", withExtension: "json"),
              let data = try? Data(contentsOf: url) else {
            fatalError("ui_test_config.json not found in test bundle.")
        }
        return try! JSONDecoder().decode(UITestConfig.self, from: data)
    }()

    /// Returns the test user for the current iOS version.
    /// Each major iOS version maps to a different user to avoid conflicts
    /// when running tests on multiple simulators in parallel.
    func user() -> User {
        let regularAuth = loginHosts.first { $0.name == "regular_auth" }!
        let major = Int(UIDevice.current.systemVersion.split(separator: ".").first ?? "0") ?? 0
        let index = major % regularAuth.users.count
        return regularAuth.users[index]
    }
}
