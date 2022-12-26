//
//  Directory.swift
//  
//
//  Created by Robert Ellis on 2021/12/07.
//

import Foundation

import Logging

public class PairHelper {
    let log = Logger(label: PairHelper.typeName)
    
    var shouldStop = false
    
    var keychainService = KeychainService.instance
    var host: String
    var port: Int
    var domain: String
    var baseUrl: String
    
    let lock = DispatchSemaphore(value: 1)
    
    public var allUris = Dictionary<String, Uri>()
    let allUrisSemaphore = DispatchSemaphore(value: 0)
    
    init(host: String,
         port: Int,
         domain: String) {
        self.host = host
        self.port = port
        self.domain = domain
        baseUrl = "http://\(host):\(port)/adsimulator/"
    }
    
    fileprivate func getUrl(_ personaUrl: String) -> String? {
        let urlParts = personaUrl.split(separator: ";")
        
        if urlParts.count < 2 {
            return nil
        }
        
        let encrUrl = urlParts[0]
        let signUrl = urlParts[1]
        
        let encrUrlParts = encrUrl.split(separator: ":")
        let signUrlParts = signUrl.split(separator: ":")
        
        if encrUrlParts.count < 2 || signUrlParts.count < 2 {
            return nil
        }
        
        let encrTxid = encrUrlParts[0]
        
        guard let encrVOut = Int(encrUrlParts[1]) else {
            log.error("Unexpected error parsing encrVOut from persona url.")
            log.warning("Exiting directory check thread due to previous error.")
            
            return nil
        }
        
        let signTxid = signUrlParts[0]
        
        guard let signVOut = Int(signUrlParts[1]) else {
            log.error("Unexpected error parsing signVOut from persona url.")
            log.warning("Exiting directory check thread due to previous error.")
            
            return nil
        }
        
        return "http://\(host):\(port)/adsimulator/uploaduri/\(domain)/\(encrTxid)/\(encrVOut)/\(signTxid)/\(signVOut)"
    }
    
    fileprivate func downloadUris(_ url: URL, completion: @escaping([String]) -> Void) {
        
        URLSession.shared.jsonDecodableTask(with: url) { [self]
            (result: Result<DirectoryDownloadStringResponse, Error>) in
            switch result {
                case .success(let response):
                    processDownloadResponse(response)
                case .failure(let error):
                    log.error("\(error)")
            }
            
            guard let url = URL(string: "http://\(host):\(port)/adsimulator/getalluri/\(domain)") else {
                log.error("Unexpected error creating url object.")
                log.warning("Exiting directory check thread due to previous error.")
                return
            }
            
            getDirectoryResponse(url, completion: {uris in
                completion(uris)
            })
            
        }.resume()
    }
    
    fileprivate func getDirectoryResponse(_ url: URL, completion: @escaping([String]) -> Void) {
        URLSession.shared.jsonDecodableTask(with: url) { [self]
            (result: Result<DirectoryGetResponse, Error>) in
            switch result {
                case .success(let response):
                    processDirectoryGetResponse(response, completion: {uris in
                        completion(uris)
                    })

                case .failure(let error):
                    log.error("\(error)")
                    completion([String]())
            }
        }.resume()
    }
    
    public func getAllUrls() -> Dictionary<String, Uri> {
        guard let url = getUrl(subDomain: "getalluri", uri: nil) else {
            log.error("Error getting URL")
            return Dictionary()
        }

        log.info("Retrieving all URIs from: \(url)")
        
        var success = false
        
        URLSession.shared.jsonDecodableTask(with: url) { [self]
            (result: Result<DirectoryGetResponse, Error>) in
            switch result {
                case .success(let response):
                    processGetAllUrlsResponse(response)
                    success = true
                    
                case .failure(let error):
                    success = false
                    log.error("\(error)")
            }
            
            allUrisSemaphore.signal()
        }.resume()
        
        let _ = allUrisSemaphore.wait(timeout: .now() + 30.0)
        
        return success ? allUris : Dictionary()
    }
    
    // Main thread loop
    // This uploads the URI of the active persona
    // Then loops to download the URIs of devices on the specified trusted directory and
    // sends pairing requests to each one, if they are not already in the set of contacts
    func getUrisFromTrustedDirectory(completion: @escaping([String]) -> Void) {
        //log.info("Directory thread started.")
        
        if shouldStop {
            return
        }
        
        guard let activePersona = keychainService?.activePersona else {
            log.error("Unexpected nil active persona.")
            log.warning("Exiting directory check thread due to previous error.")
            completion([String]())
            return
        }
        
        do {
            let personaUrl = try activePersona.getUri().toString()
            
            guard let url = getUrl(personaUrl) else {
                log.error("Unexpected error parsing persona url.")
                log.warning("Exiting directory check thread due to previous error.")
                completion([String]())
                return
            }
            
            // log.info("Sending request to upload URI to the directory. URL: \(url)")
            
            guard let url = URL(string: url) else {
                log.error("Unexpected error creating url object.")
                log.warning("Exiting directory check thread due to previous error.")
                completion([String]())
                return
            }
            
            downloadUris(url, completion: { uris in
                completion(uris)
            })

        } catch {
            log.error("\(error)")
            log.warning("Exiting directory check thread due to previous error.")
            completion([String]())
            return
        }
    }
    
    func processDownloadResponse(_ response: DirectoryDownloadStringResponse) {
        if response.response_code == "OK" {
            log.info("Blockchain id upload OK")
        } else if response.response_code == "T003_PROFILE_EXISTS" {
            // log.info("Blockchain id already uploaded")
        } else {
            log.info("Blockchain id upload failed with error: \(response.response_code)")
        }
    }
    
    func processUploadUriResponse(response: DirectoryUploadUriResponse, uri: Uri) {
        if response.response_code == "OK" {
            do {
                let uriString = try uri.toString()
                log.info("Successfully uploaded uri: \(uriString)")
            } catch {
                log.error("Error getting uri string")
            }
        } else if response.response_code == "T003_PROFILE_EXISTS" {
            log.warning("Uri upload failed with error: \(response.response_code)")
        } else {
            log.error("Uri upload failed with error: \(response.response_code)")
        }
    }
    
    func processDirectoryGetResponse(_ response: DirectoryGetResponse, completion: @escaping([String]) -> Void) {
        if response.results.isEmpty {
            log.info("No URIs to return")
            return
        }
        
        lock.wait()
        
        defer {
            lock.signal()
        }
        
        var uris = [String]()
        
        for entry in response.results {
            uris.append("\(entry.encr_txid):\(entry.encr_vout);\(entry.sign_txid):\(entry.sign_vout)")
        }

        completion(uris)
    }
    
    func processGetAllUrlsResponse(_ response: DirectoryGetResponse) {
        if response.results.isEmpty {
            log.info("No URIs to return")
            return
        }
        
        lock.wait()
        
        defer {
            lock.signal()
        }
        
        for entry in response.results {
            let uriString = "\(entry.encr_txid):\(entry.encr_vout);\(entry.sign_txid):\(entry.sign_vout)"
            
            do {
                let uri = try Uri.withUriString(uriString)
                
                if !allUris.keys.contains(uriString) {
                    log.info("Adding uri to allUris set. \(uriString)")
                    allUris[uriString] = uri
                }
            } catch {
                log.error("Error creating uri with sring: \(uriString)")
            }
        }
    }
    
    public func getUrl(subDomain: String, uri: String?) -> URL? {
        var suffix = ""
        
        if let uri = uri {
            let urlParts = uri.split(separator: ";")
            
            if urlParts.count < 2 {
                return nil
            }
            
            let encrUrl = urlParts[0]
            let signUrl = urlParts[1]
            
            let encrUrlParts = encrUrl.split(separator: ":")
            let signUrlParts = signUrl.split(separator: ":")
            
            if encrUrlParts.count < 2 || signUrlParts.count < 2 {
                return nil
            }
            
            let encrTxid = encrUrlParts[0]
            
            guard let encrVOut = Int(encrUrlParts[1]) else {
                log.error("Unexpected error parsing encrVOut from persona url.")
                log.warning("Exiting directory check thread due to previous error.")
                
                return nil
            }
            
            let signTxid = signUrlParts[0]
            
            guard let signVOut = Int(signUrlParts[1]) else {
                log.error("Unexpected error parsing signVOut from persona url.")
                log.warning("Exiting directory check thread due to previous error.")
                
                return nil
            }
            
            suffix =  "/\(encrTxid)/\(encrVOut)/\(signTxid)/\(signVOut)"
        }
        
        return URL(string: "\(baseUrl)\(subDomain)/\(domain)\(suffix)")
    }
    
    public func uploadUri(_ uri: Uri?) {
        guard uri != nil else {
            log.warning("Uri is nil")
            return;
        }
        
        do {
            guard let url = try getUrl(subDomain: "uploaduri", uri: uri?.toString()) else {
                log.error("Error getting URL")
                return
            }
            
            log.info("Uploading uri: \(url)")
            
            URLSession.shared.jsonDecodableTask(with: url) { [self]
                (result: Result<DirectoryUploadUriResponse, Error>) in
                
                defer {
                    allUrisSemaphore.signal()
                }
                
                switch result {
                        
                    case .success(let response):
                        processUploadUriResponse(response: response, uri: uri!)
                        
                    case .failure(let error):
                        log.error("\(error)")
                }
            }.resume()
            
            let _ = allUrisSemaphore.wait(timeout: .now() + 15.0)
        } catch {
            log.error("Error uploading uri: \(error)")
        }
    }
}

enum URLError: Error {
    case noData, decodingError
}

extension URLSession {
    
    /// A type safe URL loader that either completes with success value or error with Error
    func jsonDecodableTask<T: Decodable>(with url: URLRequest, decoder: JSONDecoder = JSONDecoder(), completion: @escaping (Result<T, Error>) -> Void) -> URLSessionDataTask {
        dataTask(with: url) { (data, response, error) in
            DispatchQueue.main.async {
                guard error == nil else {
                    completion(.failure(error!))
                    return
                }
                guard let data = data, let _ = response else {
                    completion(.failure(URLError.noData))
                    return
                }
                do {
                    let decoded = try decoder.decode(T.self, from: data)
                    completion(.success(decoded))
                } catch  {
                    completion(.failure(error))
                }
            }
        }
    }
    
    func jsonDecodableTask<T: Decodable>(with url: URL, decoder: JSONDecoder = JSONDecoder(), completion: @escaping (Result<T, Error>) -> Void) -> URLSessionDataTask {
        jsonDecodableTask(with: URLRequest(url: url), decoder: decoder, completion: completion)
    }
}

extension PairHelper : TypeNameDescribable {}
