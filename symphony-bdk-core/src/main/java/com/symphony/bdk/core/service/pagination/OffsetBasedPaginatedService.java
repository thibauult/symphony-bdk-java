package com.symphony.bdk.core.service.pagination;

import com.symphony.bdk.http.api.ApiException;
import com.symphony.bdk.http.api.ApiRuntimeException;

import org.apiguardian.api.API;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Retrieve a list of elements given a {@link OffsetBasedPaginatedApi}.
 * Goal is to fetch elements lazily.
 * @param <T> the type of objects to retrieve
 */
@API(status = API.Status.INTERNAL)
public class OffsetBasedPaginatedService<T> extends PaginatedService {

  private final OffsetBasedPaginatedApi<T> paginatedApi;

  /**
   * The only constructor
   *
   * @param paginatedApi the paginated api used to retrieve the chunks of elements
   * @param chunkSize the maximum number to retrieve in one call of {@link OffsetBasedPaginatedApi#get(int, int)}
   * @param maxSize the maximum number to retrieve in total
   */
  public OffsetBasedPaginatedService(OffsetBasedPaginatedApi<T> paginatedApi, Integer chunkSize, Integer maxSize) {
    super(chunkSize, maxSize);
    this.paginatedApi = paginatedApi;
  }

  /**
   * Returns an stream of elements with lazy fetching.
   *
   * @return a {@link Stream} which lazily makes calls to the {@link #paginatedApi}.
   */
  public Stream<T> stream() {
    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new PaginatedIterator(), Spliterator.ORDERED), false);
  }

  @API(status = API.Status.INTERNAL)
  private class PaginatedIterator implements Iterator<T> {

    private int currentChunkSize;
    private List<T> remainingItemsInChunk;
    private int fetchedItems;
    private int currentOffset;

    public PaginatedIterator() {
      currentChunkSize = -1;
      remainingItemsInChunk = Collections.emptyList();
      fetchedItems = 0;
      currentOffset = 0;
    }

    @Override
    public boolean hasNext() {
      // we already fetched maxSize items
      if (fetchedItems == maxSize) {
        return false;
      }

      // we didn't fetch maxSize items and we have remaining items in the chunk
      if (!remainingItemsInChunk.isEmpty()) {
        return true;
      }

      // no remaining items in chunk but chunk was already smaller than chunkSize: we are already at the end
      if (fetchedItems > 0 && currentChunkSize < chunkSize) {
        return false;
      }

      // no remaining items in chunk, let's fetch a new one
      fetchNewChunk();

      return !remainingItemsInChunk.isEmpty();
    }

    private void fetchNewChunk() {
      try {
        final List<T> currentChunk = paginatedApi.get(currentOffset, chunkSize);
        remainingItemsInChunk = currentChunk == null ? Collections.emptyList() : new ArrayList<>(currentChunk);
        currentChunkSize = remainingItemsInChunk.size();
        currentOffset += chunkSize;
      } catch (ApiException e) {
        throw new ApiRuntimeException(e);
      }
    }

    @Override
    public T next() {
      fetchedItems++;
      return remainingItemsInChunk.remove(0);
    }
  }

}
