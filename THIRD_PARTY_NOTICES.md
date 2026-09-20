# Third-Party Notices

This project includes portions derived from PixelPlayer.

Project: PixelPlayer  
Repository: https://github.com/theovilardo/PixelPlayer  
Original author/maintainer: Theo Vilardo / theovilardo  
License: MIT License, for the portions used from the MIT-licensed version of PixelPlayer

The original MIT-licensed portions remain subject to the MIT License. The
original copyright notice and permission notice are retained below.

Important: Pixel Music should only include PixelPlayer code that was obtained
while that code/version was available under the MIT License. PixelPlayer code
released only under a later proprietary license should not be imported without
written permission from the applicable rights holder.

The MIT License text below is retained as it appeared in the upstream
MIT-licensed source snapshot used for this project.

-------------------------------------------------------------------------------

MIT License

Copyright (c) 2024 [Full Name]

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


-------------------------------------------------------------------------------

## ArchiveTune (lossless streaming / Source Pool integration)

Project: ArchiveTune  
Repository: https://github.com/4nx3b/ArchiveTune  
Original author/maintainer: Rukamori (github.com/rukamori) and contributors  
License: GNU General Public License v3.0 (GPL-3.0)

The lossless-source stack under `app/src/main/java/com/unshoo/pixelmusic/data/lossless/`
(Source Pool client + AES-256-GCM feed decryption, Tidal / Qobuz / Deezer / Apple Music
stream resolvers, Deezer Blowfish-CBC decrypting DataSource, Tidal progressive-DASH
DataSource, Apple Music HLS flattening + Widevine license callback, track-matching gate,
instance health/cooldown handling), the provider sign-in screens under
`presentation/screens/lossless/`, and the background `SourceRefreshWorker` are ported from
ArchiveTune (2026) with package renames and adaptations to PixelMusic's player pipeline.
Per GPL-3.0 §4/§5 the original copyright notices are retained in the file headers.
PixelMusic itself is distributed under the GNU GPL, so the combined work stays GPL-3.0.
