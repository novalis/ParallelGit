package com.beijunyi.parallelgit;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.beijunyi.parallelgit.utils.*;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

public abstract class AbstractParallelGitTest {

  protected File repoDir;
  protected Repository repo;
  protected DirCache cache;

  @Before
  public void setUpCache() {
    cache = DirCache.newInCore();
  }

  @After
  public void closeRepository() throws IOException {
    if(repo != null)
      repo.close();
    if(repoDir != null && repoDir.exists())
      FileUtils.delete(repoDir, FileUtils.RECURSIVE);
  }

  @Nonnull
  protected ObjectId writeToCache(String path, byte[] content, FileMode mode) throws IOException {
    ObjectId blobId = repo != null ? ObjectUtils.insertBlob(content, repo) : calculateBlobId(content);
    CacheUtils.addFile(path, mode, blobId, cache);
    return blobId;
  }

  @Nonnull
  protected ObjectId writeToCache(String path, byte[] content) throws IOException {
    return writeToCache(path, content, FileMode.REGULAR_FILE);
  }

  @Nonnull
  protected ObjectId writeToCache(String path, String content) throws IOException {
    return writeToCache(path, Constants.encode(content));
  }

  @Nonnull
  protected ObjectId writeToCache(String path) throws IOException {
    return writeToCache(path, someBytes());
  }

  @Nonnull
  protected ObjectId writeSomethingToCache() throws IOException {
    return writeToCache(UUID.randomUUID().toString() + ".txt");
  }

  protected void writeMultipleToCache(String... paths) throws IOException {
    DirCacheBuilder builder = CacheUtils.keepEverything(cache);
    for(String path : paths) {
      AnyObjectId blobId = repo != null ? ObjectUtils.insertBlob(someBytes(), repo) : someObjectId();
      CacheUtils.addFile(path, FileMode.REGULAR_FILE, blobId, builder);
    }
    builder.finish();
  }

  @Nonnull
  protected ObjectId updateFile(String path, byte[] content) throws IOException {
    ObjectId blobId = ObjectUtils.insertBlob(content, repo);
    CacheUtils.updateFileBlob(path, blobId, cache);
    return blobId;
  }

  @Nonnull
  protected ObjectId updateFile(String path, String content) throws IOException {
    return updateFile(path, Constants.encode(content));
  }

  @Nonnull
  protected String someText() {
    return UUID.randomUUID().toString();
  }

  @Nonnull
  protected String someFilename() {
    return someText() + ".txt";
  }

  @Nonnull
  protected byte[] someBytes() {
    return someText().getBytes();
  }

  @Nonnull
  protected ObjectId someObjectId() {
    return calculateBlobId(someBytes());
  }

  @Nonnull
  protected String someCommitMessage() {
    return getClass().getSimpleName() + " commit: " + someText();
  }

  @Nonnull
  protected PersonIdent somePersonIdent() {
    String name = getClass().getSimpleName();
    return new PersonIdent(name, name + "@test.com");
  }

  @Nonnull
  protected RevCommit commit(String message, @Nullable AnyObjectId parent) throws IOException {
    return CommitUtils.createCommit(message, cache, somePersonIdent(), parent, repo);
  }

  @Nonnull
  protected RevCommit commit(@Nullable AnyObjectId parent) throws IOException {
    return commit(someCommitMessage(), parent);
  }

  @Nonnull
  protected RevCommit commit() throws IOException {
    return commit(null);
  }

  protected void updateBranchHead(String branch, AnyObjectId commit, boolean init) throws IOException {
    if(init)
      BranchUtils.initBranch(branch, commit, repo);
    else
      BranchUtils.newCommit(branch, commit, repo);
  }

  @Nonnull
  protected RevCommit commitToBranch(String branch, String message, @Nullable AnyObjectId parent) throws IOException {
    if(parent == null && BranchUtils.branchExists(branch, repo))
      parent = BranchUtils.getHeadCommit(branch, repo);
    RevCommit commitId = commit(message, parent);
    updateBranchHead(branch, commitId, parent == null);
    return commitId;
  }

  @Nonnull
  protected RevCommit commitToBranch(String branch, @Nullable AnyObjectId parent) throws IOException {
    return commitToBranch(branch, someCommitMessage(), parent);
  }

  @Nonnull
  protected RevCommit commitToBranch(String branch) throws IOException {
    return commitToBranch(branch, someCommitMessage(), null);
  }

  @Nonnull
  protected RevCommit commitToMaster(String message, @Nullable AnyObjectId parent) throws IOException {
    return commitToBranch(Constants.MASTER, message, parent);
  }

  @Nonnull
  protected RevCommit commitToMaster(String message) throws IOException {
    return commitToBranch(Constants.MASTER, message, null);
  }

  @Nonnull
  protected RevCommit commitToMaster() throws IOException {
    return commitToBranch(Constants.MASTER);
  }

  protected void clearCache() {
    cache = DirCache.newInCore();
  }

  protected void initRepositoryDir() throws IOException {
    if(repoDir == null)
      repoDir = FileUtils.createTempDir(getClass().getSimpleName(), null, null);
  }

  @Nonnull
  protected RevCommit initContent() throws IOException {
    writeToCache("existing_file.txt");
    commitToMaster();
    writeToCache("some_other_file.txt");
    RevCommit head = commitToMaster();
    clearCache();
    return head;
  }

  @Nonnull
  protected RevCommit initRepository(boolean memory, boolean bare) throws IOException {
    if(!memory)
      initRepositoryDir();
    repo = memory ? new TestRepository(bare) : RepositoryUtils.createRepository(repoDir, bare);
    return initContent();
  }

  @Nonnull
  protected RevCommit initFileRepository(boolean bare) throws IOException {
    return initRepository(false, bare);
  }

  @Nonnull
  protected RevCommit initMemoryRepository(boolean bare) throws IOException {
    return initRepository(true, bare);
  }

  @Nonnull
  protected RevCommit initRepository() throws IOException {
    return initRepository(true, true);
  }

  @Nonnull
  public static ObjectId calculateBlobId(byte[] data) {
    return new ObjectInserter.Formatter().idFor(Constants.OBJ_BLOB, data);
  }

  public static void assertCacheEquals(@Nullable String message, DirCache expected, DirCache actual) {
    if(expected != actual) {
      String header = message == null ? "" : message + ": ";
      int cacheSize = assertCacheSameSize(expected, actual, header);
      DirCacheEntry[] expectedEntries = expected.getEntriesWithin("");
      DirCacheEntry[] actualEntries = actual.getEntriesWithin("");
      for(int i = 0; i < cacheSize; ++i) {
        DirCacheEntry expectedEntry = expectedEntries[i];
        DirCacheEntry actualEntry = actualEntries[i];
        assertCacheEntryEquals(expectedEntry, actualEntry, header, i);
      }
    }
  }

  public static void assertCacheEquals(DirCache expected, DirCache actual) {
    assertCacheEquals(null, expected, actual);
  }

  private static int assertCacheSameSize(DirCache expected, DirCache actual, String header) {
    int actualSize = actual.getEntryCount();
    int expectedSize = expected.getEntryCount();
    if(actualSize != expectedSize)
      Assert.fail(header + "cache sizes differed, expected.size=" + expectedSize + " actual.size=" + actualSize);
    return expectedSize;
  }

  private static void assertCacheEntryEquals(DirCacheEntry expected, DirCacheEntry actual, String header, int index) {
    try {
      Assert.assertEquals("fileMode", expected.getFileMode(), actual.getFileMode());
      Assert.assertEquals("length", expected.getLength(), actual.getLength());
      Assert.assertEquals("objectId", expected.getObjectId(), actual.getObjectId());
      Assert.assertEquals("stage", expected.getStage(), actual.getStage());
      Assert.assertEquals("path", expected.getPathString(), actual.getPathString());
    } catch(AssertionError e) {
      Assert.fail(header + "caches first differed at entry [" + index + "]; " + e.getMessage());
    }
  }

  protected class TestRepository extends InMemoryRepository {

    private final File directory;
    private final File workTree;

    public TestRepository(boolean bare) {
      super(new DfsRepositoryDescription());
      File mockLocation = new File(System.getProperty("java.io.tmpdir"));
      directory = bare ? mockLocation : new File(mockLocation, Constants.DOT_GIT);
      workTree = bare ? null : mockLocation;
    }

    public TestRepository() {
      this(true);
    }

    @Override
    public boolean isBare() {
      return workTree == null;
    }

    @Nonnull
    @Override
    public File getWorkTree() throws NoWorkTreeException {
      if(workTree == null)
        throw new NoWorkTreeException();
      return workTree;
    }

    @Nonnull
    @Override
    public File getDirectory() {
      return directory;
    }

  }

}
