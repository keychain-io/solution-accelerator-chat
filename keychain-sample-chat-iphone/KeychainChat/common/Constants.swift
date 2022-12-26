//
//  Constants.swift
//  KeychainChat
//
//  Created by Robert Ellis on 10/17/21.
//

import Foundation

struct Constants {
    // MARK: Other constants
    static let appName = "KeychainChat"
    static let addPersona = "Add Persona"
    static let editPersona = "Edit Persona"
    static let editContact = "Edit Contact"
    static let lastNamePrompt = "Sub Name"
    static let firstNamePrompt = "Name"
    static let personas = "Personas"
    static let savingPersona = "Saving persona:"
    static let renamingPersona = "Renaming persona:"
    static let renamingContact = "Renaming contact:"
    static let savedSuccessfullyTitle = "Saved Successfully"
    static let errorTitle = "Error"
    static let personaSavedSuccessfully = "Persona saved successfully"
    static let personaSaveFailed = "Persona could not be saved"
    static let firstAndLastNamesRequired = "First and last names are required"
    static let gateway = "Gateway"
    static let pairingChannel = "ios/test/pairing/"
    static let facade = "Facade"
    static let logout = "Logout"
    static let logoutImageName = "arrowshape.turn.up.right.circle"

    static let oneHalfSecond:UInt32 = 500000

    static let all = "ALL"

    // MARK: Errors
    static let somethingWentWrong = "Something went wrong!"
    static let errorGettingContactSubname = "Error getting contact subname:"
    static let noActivePersona = "No active persona."
    static let failedToPair = "Failed to pair."
    static let errorMakingPairingResponse = "Error making pairing response message."
    static let errorMakingPairingAck = "Error making pairing ack message."
    static let noDatabaseConnection = "No database connection."

    // MARK: Local DB
    static let chats = "chats"
    static let sqliteExtention = "sqlite3"
    static let sql = "sql"

    static let xmlStartEncoded = "%3C%3Fxml%20"
}
