import { Injectable } from '@angular/core';
// import { Base64 } from 'crypto-js/enc-base64';
import * as CryptoJS from 'crypto-js';
/* import sha256 from 'crypto-js/sha256';
import hmacSHA512 from 'crypto-js/hmac-sha512';
import Base64 from 'crypto-js/enc-base64';
*/


@Injectable({
  providedIn: 'root'
})
export class Base64HasherService {

  base64 = CryptoJS.enc.Base64;

  constructor() { }

  encrypt(log: string, pass: string): string {

    // WordArray.

    // const message, nonce, path, privateKey; // ...
    // const hashDigest = CryptoJS.SHA256 sha256(nonce + message);
    // const hmacDigest = Base64.stringify(hmacSHA512(path + hashDigest, privateKey));
    const hash = CryptoJS.SHA256 (`${log}:::${pass}`);
    console.log('hash: ', hash);

    const str = hash.toString(CryptoJS.enc.Base64); // hashed
    console.log("str: " + str);

    const words = CryptoJS.enc.Base64.parse("mama miała kota", );
    console.log("words: " + words);

    const stringified = CryptoJS.enc.Base64.stringify(words)
    console.log("stringified: ", stringified);

    // CryptoJS.enc.Base64.
    var wordss = CryptoJS.enc.Base64.parse("SGVsbG8sIFdvcmxkIQ==");
    console.log('wordss: ', wordss);
    var base64 = CryptoJS.enc.Base64.stringify(wordss);
    console.log('base64: ', base64)
    return stringified;
  }
  //   const wa = CryptoJS.lib.WordArray
  //   this.base64.stringify(["alskdjf","laksdjfh"]);
  // }



}
