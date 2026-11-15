package com.bxostock.app;

import androidx.core.content.FileProvider;

/** Separate from any other FileProvider in the app, so the manifests do not clash. */
public class PhotoFileProvider extends FileProvider {
}