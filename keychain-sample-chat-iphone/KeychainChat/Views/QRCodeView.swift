//
//  QRCodeView.swift
//  ConsumerApp
//
//  Created by Robert Ellis on 9/12/21.
//

import SwiftUI
import Foundation
import CoreImage.CIFilterBuiltins

import Logging

struct QRCodeView: View {
    
    let log = Logger(label: ContactViewModel.typeName)

    let filter = CIFilter.qrCodeGenerator()
    let context = CIContext()
    
    var width: CGFloat = 250
    var height: CGFloat = 250
    var alignment: Alignment = .center
    var stringData: String
    
    var body: some View {
        Image(uiImage: generateQRcodeImage(stringData: stringData))
            .interpolation(.none)
            .resizable()
            .aspectRatio(contentMode: .fit)
            .frame(width: width, height: height, alignment: alignment)
    }
    
    func generateQRcodeImage(stringData: String) -> UIImage {
        var stringData = stringData.replacingOccurrences(of: "\n", with: "")
        stringData = stringData.replacingOccurrences(of: " ", with: "")

        log.info("Creating QR Code from: \(stringData.utf8)")
        let data = Data(stringData.utf8Bytes)
        filter.setValue(data, forKey: "inputMessage")

        if let qrCodeImage = filter.outputImage {
            if let qrCodeCGImage = context.createCGImage(qrCodeImage, from: qrCodeImage.extent) {
                return UIImage(cgImage: qrCodeCGImage)
            }
        }

        return UIImage(systemName: "xmark") ?? UIImage()
    }
}

struct QRCodeView_Previews: PreviewProvider {
    static var previews: some View {
        QRCodeView(stringData: "000000")
    }
}

extension QRCodeView : TypeNameDescribable {}
