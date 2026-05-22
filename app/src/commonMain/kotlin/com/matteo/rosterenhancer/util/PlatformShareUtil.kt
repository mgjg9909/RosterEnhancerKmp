package com.matteo.rosterenhancer.util

/**
 * Funzione KMP per condividere un testo tramite il sistema nativo.
 * Su Android usa Intent.ACTION_SEND.
 * Su iOS usa UIActivityViewController.
 */
expect fun shareText(text: String)
