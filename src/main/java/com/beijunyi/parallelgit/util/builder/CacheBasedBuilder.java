package com.beijunyi.parallelgit.util.builder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.beijunyi.parallelgit.util.DirCacheHelper;
import com.beijunyi.parallelgit.util.RevTreeHelper;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;

public abstract class CacheBasedBuilder<B extends CacheBasedBuilder, T> extends ParallelBuilder<T> {

  protected AnyObjectId baseCommitId;
  protected String baseCommitIdStr;
  protected AnyObjectId baseTreeId;
  protected String baseTreeIdStr;
  protected final List<CacheEditor> editors = new ArrayList<>();
  protected final Repository repository;

  protected CacheBasedBuilder(@Nullable Repository repository) {
    this.repository = repository;
  }

  @Nonnull
  protected abstract B self();

  @Nonnull
  public B baseCommit(@Nonnull AnyObjectId commitId) {
    this.baseCommitId = commitId;
    return self();
  }

  @Nonnull
  public B baseCommit(@Nonnull String commitIdStr) {
    this.baseCommitIdStr = commitIdStr;
    return self();
  }

  @Nonnull
  public B baseTree(@Nonnull AnyObjectId treeId) {
    this.baseTreeId = treeId;
    return self();
  }

  @Nonnull
  public B baseTree(@Nonnull String treeIdStr) {
    this.baseTreeIdStr = treeIdStr;
    return self();
  }

  @Nonnull
  public B addTree(@Nonnull AnyObjectId treeId, @Nonnull String path) {
    AddTree editor = new AddTree(path);
    editor.setTreeId(treeId);
    editors.add(editor);
    return self();
  }

  @Nonnull
  public B addTree(@Nonnull String treeIdStr, @Nonnull String path) {
    AddTree editor = new AddTree(path);
    editor.setTreeIdStr(treeIdStr);
    editors.add(editor);
    return self();
  }

  @Nonnull
  public B addBlob(@Nonnull AnyObjectId blobId, @Nonnull FileMode mode, @Nonnull String path) {
    AddBlob editor = new AddBlob(path);
    editor.setBlobId(blobId);
    editor.setMode(mode);
    editors.add(editor);
    return self();
  }

  @Nonnull
  public B addBlob(@Nonnull AnyObjectId blobId, @Nonnull String path) {
    return addBlob(blobId, FileMode.REGULAR_FILE, path);
  }

  @Nonnull
  public B deleteTree(@Nonnull String path) {
    DeleteTree editor = new DeleteTree(path);
    editors.add(editor);
    return self();
  }

  @Nonnull
  public B deleteBlob(@Nonnull String path) {
    DeleteBlob editor = new DeleteBlob(path);
    editors.add(editor);
    return self();
  }

  @Nonnull
  public B updateBlob(@Nonnull AnyObjectId blobId, @Nonnull FileMode mode, @Nonnull String path) {
    UpdateBlob editor = new UpdateBlob(path);
    editor.setBlobId(blobId);
    editor.setMode(mode);
    editors.add(editor);
    return self();
  }

  @Nonnull
  public B updateBlob(@Nonnull AnyObjectId blobId, @Nonnull String path) {
    UpdateBlob editor = new UpdateBlob(path);
    editor.setBlobId(blobId);
    editors.add(editor);
    return self();
  }

  @Nonnull
  public B updateBlob(@Nonnull FileMode mode, @Nonnull String path) {
    UpdateBlob editor = new UpdateBlob(path);
    editor.setMode(mode);
    editors.add(editor);
    return self();
  }

  private void setupBase(@Nonnull BuildStateProvider provider) throws IOException {
    if(baseTreeId != null || baseTreeIdStr != null || baseCommitId != null || baseCommitIdStr != null) {
      if(baseTreeId == null) {
        if(baseTreeIdStr != null)
          baseTreeId = provider.getRepository().resolve(baseTreeIdStr);
        else {
          if(baseCommitId == null)
            baseCommitId = provider.getRepository().resolve(baseCommitIdStr);
          baseTreeId = RevTreeHelper.getRootTree(provider.getCurrentReader(), baseCommitId);
        }
      }
      DirCacheHelper.addTree(provider.getCurrentCache(), provider.getCurrentReader(), "", baseTreeId);
    }
  }

  @Nonnull
  protected DirCache buildCache() throws IOException {
    try(BuildStateProvider provider = new BuildStateProvider(repository)) {
      setupBase(provider);
      for(CacheEditor editor : editors) {
        editor.doEdit(provider);
      }
      return provider.getCurrentCache();
    }
  }
}
