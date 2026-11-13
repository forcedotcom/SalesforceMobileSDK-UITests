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
    func user(host: String = "regular_auth") -> User {
        guard let loginHost = loginHosts.first(where: { $0.name == host }) else {
            fatalError("Login host '\(host)' not found in ui_test_config.json.")
        }
        var major = Int(UIDevice.current.systemVersion.split(separator: ".").first ?? "0") ?? 0
        // TODO: Remove this special case when iOS 18 support is dropped.
        // The user index is the major version modulo the number of users on the host. With only
        // 3 users (native_login), iOS 18 and iOS 27 both give index 0 and would share an account.
        // Using 28 in place of 18 gives iOS 18 index 1 instead, so 18, 26 and 27 get indexes
        // 1, 2 and 0. On hosts with 5 users, 28 gives the same index as 18 (3), so those users
        // are unchanged.
        if major == 18 { major = 28 }
        let index = major % loginHost.users.count
        return loginHost.users[index]
    }
}
