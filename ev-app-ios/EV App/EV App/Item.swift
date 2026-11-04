//
//  Item.swift
//  EV App
//
//  Created by Kevin Vavelin on 04/11/2026.
//

import Foundation
import SwiftData

@Model
final class Item {
    var timestamp: Date
    
    init(timestamp: Date) {
        self.timestamp = timestamp
    }
}
