package com.symphony.bdk.http.jersey2;

import com.symphony.bdk.http.api.ApiClient;
import com.symphony.bdk.http.api.ApiClientBodyPart;
import com.symphony.bdk.http.api.ApiException;
import com.symphony.bdk.http.api.ApiResponse;
import com.symphony.bdk.http.api.Pair;
import com.symphony.bdk.http.api.auth.Authentication;
import com.symphony.bdk.http.api.tracing.DistributedTracingContext;
import com.symphony.bdk.http.api.util.TypeReference;

import org.apache.http.NoHttpResponseException;
import org.apache.http.conn.ConnectTimeoutException;
import org.apiguardian.api.API;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartMediaTypes;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * Jersey2 implementation for the {@link ApiClient} interface called by generated code.
 */
@API(status = API.Status.STABLE)
public class ApiClientJersey2 implements ApiClient {

  protected Client httpClient;
  protected String basePath;
  protected Map<String, String> defaultHeaderMap;
  protected String tempFolderPath;
  protected Map<String, Authentication> authentications;
  protected List<String> enforcedAuthenticationSchemes;

  public ApiClientJersey2(final Client httpClient, String basePath, Map<String, String> defaultHeaders,
      String temporaryFolderPath) {
    this.httpClient = httpClient;
    this.basePath = basePath;
    this.defaultHeaderMap = new HashMap<>(defaultHeaders);
    this.tempFolderPath = temporaryFolderPath;
    this.authentications = new HashMap<>();
    this.enforcedAuthenticationSchemes = new ArrayList<>();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> ApiResponse<T> invokeAPI(
      final String path,
      final String method,
      final List<Pair> queryParams,
      final Object body,
      final Map<String, String> headerParams,
      final Map<String, String> cookieParams,
      final Map<String, Object> formParams,
      final String accept,
      final String contentType,
      final String[] authNames,
      final TypeReference<T> returnType
  ) throws ApiException {

    // Not using `.target(this.basePath).path(path)` below,
    // to support (constant) query string in `path`, e.g. "/posts?draft=1"
    WebTarget target = httpClient.target(this.basePath + path);

    this.updateParamsForAuth(authNames, headerParams);

    if (queryParams != null) {
      for (Pair queryParam : queryParams) {
        if (queryParam.getValue() != null) {
          target = target.queryParam(queryParam.getName(), escapeString(queryParam.getValue()));
        }
      }
    }

    Invocation.Builder invocationBuilder = target.request().accept(accept);
    boolean clearTraceId = false;

    if (!DistributedTracingContext.hasTraceId()) {
      DistributedTracingContext.setTraceId();
      clearTraceId = true;
    }

    invocationBuilder =
        invocationBuilder.header(DistributedTracingContext.TRACE_ID, DistributedTracingContext.getTraceId());

    if (headerParams != null) {
      for (Entry<String, String> entry : headerParams.entrySet()) {
        String value = entry.getValue();
        if (value != null) {
          invocationBuilder = invocationBuilder.header(entry.getKey(), value);
        }
      }
    }

    if (cookieParams != null) {
      for (Entry<String, String> entry : cookieParams.entrySet()) {
        String value = entry.getValue();
        if (value != null) {
          invocationBuilder = invocationBuilder.cookie(entry.getKey(), value);
        }
      }
    }

    // apply default headers, that can be set from config.yaml
    for (Entry<String, String> entry : defaultHeaderMap.entrySet()) {
      String key = entry.getKey();
      if (!headerParams.containsKey(key)) {
        String value = entry.getValue();
        if (value != null) {
          invocationBuilder = invocationBuilder.header(key, value);
        }
      }
    }

    // https://eclipse-ee4j.github.io/jersey.github.io/documentation/latest/client.html#connectors.warning
    // by setting this header now instead of org.glassfish.jersey.media.multipart.internal.MultiPartWriter
    // we avoid the warning
    if (contentType.startsWith(MediaType.MULTIPART_FORM_DATA)) {
      invocationBuilder.header("MIME-Version", "1.0");
    }

    Entity<?> entity =
        (body == null && formParams == null) ? Entity.json("") : this.serialize(body, formParams, contentType);

    try (Response response = getResponse(invocationBuilder, method, entity)) {

      int statusCode = response.getStatusInfo().getStatusCode();
      Map<String, List<String>> responseHeaders = buildResponseHeaders(response);

      GenericType<T> genericReturnType = null;
      if (returnType != null) {
        genericReturnType = new GenericType<>(returnType.getType());
      }

      if (response.getStatus() == Status.NO_CONTENT.getStatusCode()) {
        return new ApiResponse<>(statusCode, responseHeaders);
      } else if (response.getStatusInfo().getFamily() == Status.Family.SUCCESSFUL) {
        if (genericReturnType == null) {
          return new ApiResponse<>(statusCode, responseHeaders);
        } else {
          return new ApiResponse<>(statusCode, responseHeaders, deserialize(response, genericReturnType));
        }
      } else {
        String message = "error";
        String respBody = null;
        if (response.hasEntity()) {
          try {
            respBody = String.valueOf(response.readEntity(String.class));
            message = respBody;
          } catch (RuntimeException e) {
            // ignored if we cannot read the response body
          }
        }
        throw new ApiException(
            response.getStatus(),
            message,
            buildResponseHeaders(response),
            respBody);
      }
    } finally {
      if (clearTraceId) {
        DistributedTracingContext.clear();
      }
    }
  }

  private Response getResponse(Invocation.Builder invocationBuilder, String method, Entity<?> entity)
      throws ApiException {
    try {
      switch (method) {
        case HttpMethod.GET:
          return invocationBuilder.get();
        case HttpMethod.POST:
          return invocationBuilder.post(entity);
        case HttpMethod.PUT:
          return invocationBuilder.put(entity);
        case HttpMethod.DELETE:
          return invocationBuilder.method(HttpMethod.DELETE, entity);
        case HttpMethod.PATCH:
          return invocationBuilder.method(HttpMethod.PATCH, entity);
        case HttpMethod.HEAD:
          return invocationBuilder.head();
        case HttpMethod.OPTIONS:
          return invocationBuilder.options();
        case "TRACE":
          return invocationBuilder.trace();
        default:
          throw new ApiException(500, "unknown method type " + method);
      }
    } catch (ProcessingException e) {
      if (e.getCause() instanceof ConnectTimeoutException) {
        throw new ProcessingException(new SocketTimeoutException(e.getCause().getMessage()));
      }
      else if (e.getCause() instanceof NoHttpResponseException) {
        // ensures that it will be caught later in the retry strategy
        throw new ProcessingException(new SocketException(e.getCause().getMessage()));
      }
      else {
        throw e;
      }
    }
  }

  @Override
  public String getBasePath() {
    return basePath;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String parameterToString(Object param) {
    if (param == null) {
      return "";
    } else if (param instanceof Collection) {
      StringBuilder b = new StringBuilder();
      for (Object o : (Collection<?>) param) {
        if (b.length() > 0) {
          b.append(',');
        }
        b.append(o);
      }
      return b.toString();
    } else {
      return String.valueOf(param);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Pair> parameterToPairs(String collectionFormat, String name, Object value) {
    List<Pair> params = new ArrayList<>();

    // preconditions
    if (name == null || name.isEmpty() || value == null) {
      return params;
    }

    Collection<?> valueCollection;
    if (value instanceof Collection) {
      valueCollection = (Collection) value;
    } else {
      params.add(new Pair(name, parameterToString(value)));
      return params;
    }

    if (valueCollection.isEmpty()) {
      return params;
    }

    // get the collection format (default: csv)
    String format = (collectionFormat == null || collectionFormat.isEmpty() ? "csv" : collectionFormat);

    // create the params based on the collection format
    if ("multi".equals(format)) {
      for (Object item : valueCollection) {
        params.add(new Pair(name, parameterToString(item)));
      }

      return params;
    }

    String delimiter = ",";

    switch (format) {
      case "csv":
        delimiter = ",";
        break;
      case "ssv":
        delimiter = " ";
        break;
      case "tsv":
        delimiter = "\t";
        break;
      case "pipes":
        delimiter = "|";
        break;
    }

    StringBuilder sb = new StringBuilder();
    for (Object item : valueCollection) {
      sb.append(delimiter);
      sb.append(parameterToString(item));
    }

    params.add(new Pair(name, sb.substring(1)));

    return params;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String selectHeaderAccept(String[] accepts) {
    if (accepts.length == 0) {
      return null;
    }
    for (String accept : accepts) {
      if (isJsonMime(accept)) {
        return accept;
      }
    }
    return String.join(",", accepts);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String selectHeaderContentType(String[] contentTypes) {
    if (contentTypes.length == 0) {
      return "application/json";
    }
    for (String contentType : contentTypes) {
      if (isJsonMime(contentType)) {
        return contentType;
      }
    }
    return contentTypes[0];
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String escapeString(String str) {
    try {
      return URLEncoder.encode(str, "utf8").replaceAll("\\+", "%20");
    } catch (UnsupportedEncodingException e) {
      return str;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, Authentication> getAuthentications() {
    return this.authentications;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void addEnforcedAuthenticationScheme(String name) {
    this.enforcedAuthenticationSchemes.add(name);
  }

  /**
   * Check if the given MIME is a JSON MIME.
   * JSON MIME examples:
   * application/json
   * application/json; charset=UTF8
   * APPLICATION/JSON
   * application/vnd.company+json
   * "* / *" is also default to JSON
   *
   * @param mime MIME
   * @return True if the MIME type is JSON
   */
  protected boolean isJsonMime(String mime) {
    String jsonMime = "(?i)^(application/json|[^;/ \t]+/[^;/ \t]+[+]json)[ \t]*(;.*)?$";
    return mime != null && (mime.matches(jsonMime) || mime.equals("*/*"));
  }

  /**
   * Serialize the given Java object into string entity according the given
   * Content-Type (only JSON is supported for now).
   *
   * @param obj         Object
   * @param formParams  Form parameters
   * @param contentType Context type
   * @return Entity
   */
  protected Entity<?> serialize(Object obj, Map<String, Object> formParams, String contentType) {
    if (contentType.startsWith(MediaType.MULTIPART_FORM_DATA)) {
      return this.serializeMultiPartFormDataEntity(formParams);
    } else if (contentType.startsWith(MediaType.APPLICATION_FORM_URLENCODED)) {
      final Form form = new Form();
      formParams.forEach((key, value) -> form.param(key, parameterToString(value)));
      return Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE);
    } else {
      // We let jersey handle the serialization
      return Entity.entity(obj, contentType);
    }
  }

  private Entity<?> serializeMultiPartFormDataEntity(Map<String, Object> formParams) {

    FormDataMultiPart multiPart = new FormDataMultiPart();

    for (final Entry<String, Object> param : formParams.entrySet()) {
      // if part is a File
      if (param.getValue() instanceof File) {
        final File file = (File) param.getValue();
        final FormDataContentDisposition contentDisposition = FormDataContentDisposition
            .name(param.getKey())
            .fileName(file.getName())
            .size(file.length())
            .build();
        final FormDataBodyPart streamPart = new FormDataBodyPart(
            contentDisposition,
            file,
            MediaType.APPLICATION_OCTET_STREAM_TYPE
        );
        multiPart = (FormDataMultiPart) multiPart.bodyPart(streamPart);
      }
      // if part is a ApiClientBodyPart[]
      else if (param.getValue() instanceof ApiClientBodyPart[]) {
        for (ApiClientBodyPart attachment : (ApiClientBodyPart[]) param.getValue()) {
          final StreamDataBodyPart streamPart =
              new StreamDataBodyPart(param.getKey(), attachment.getContent(), attachment.getFilename());
          multiPart = (FormDataMultiPart) multiPart.bodyPart(streamPart);
        }
      }
      // if part is a single ApiClientBodyPart
      else if (param.getValue() instanceof ApiClientBodyPart) {
        final ApiClientBodyPart part = (ApiClientBodyPart) param.getValue();
        final StreamDataBodyPart streamPart =
            new StreamDataBodyPart(param.getKey(), part.getContent(), part.getFilename());
        multiPart = (FormDataMultiPart) multiPart.bodyPart(streamPart);
      } else {
        multiPart = multiPart.field(param.getKey(), this.parameterToString(param.getValue()));
      }
    }

    return Entity.entity(multiPart, MultiPartMediaTypes.createFormData());
  }

  /**
   * Deserialize response body to Java object according to the Content-Type.
   *
   * @param <T>        Type
   * @param response   Response
   * @param returnType Return type
   * @return Deserialize object
   * @throws ApiException API exception
   */
  @SuppressWarnings("unchecked")
  protected <T> T deserialize(Response response, GenericType<T> returnType) throws ApiException {
    if (response == null || returnType == null) {
      return null;
    }

    if ("byte[]".equals(returnType.toString())) {
      // Handle binary response (byte array).
      return (T) response.readEntity(byte[].class);
    } else if (returnType.getRawType() == File.class) {
      // Handle file downloading.
      return (T) downloadFileFromResponse(response);
    }

    return response.readEntity(returnType);
  }

  /**
   * Download file from the given response.
   *
   * @param response Response
   * @return File
   * @throws ApiException If fail to read file content from response and write to disk
   */
  protected File downloadFileFromResponse(final Response response) throws ApiException {
    try {
      final File file = this.prepareDownloadFile(response);
      Files.copy(response.readEntity(InputStream.class), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
      return file;
    } catch (IOException e) {
      throw new ApiException("Unable to download file from response", e);
    }
  }

  protected File prepareDownloadFile(Response response) throws IOException {
    String filename = null;
    String contentDisposition = (String) response.getHeaders().getFirst("Content-Disposition");
    if (contentDisposition != null && !"".equals(contentDisposition)) {
      // Get filename from the Content-Disposition header.
      Pattern pattern = Pattern.compile("filename=['\"]?([^'\"\\s]+)['\"]?");
      Matcher matcher = pattern.matcher(contentDisposition);
      if (matcher.find()) {
        filename = matcher.group(1);
      }
    }

    String prefix;
    String suffix = null;
    if (filename == null) {
      prefix = "download-";
      suffix = "";
    } else {
      int pos = filename.lastIndexOf('.');
      if (pos == -1) {
        prefix = filename + "-";
      } else {
        prefix = filename.substring(0, pos) + "-";
        suffix = filename.substring(pos);
      }
      // File.createTempFile requires the prefix to be at least three characters long
      if (prefix.length() < 3) {
        prefix = "download-";
      }
    }

    if (tempFolderPath == null) {
      return File.createTempFile(prefix, suffix);
    } else {
      return File.createTempFile(prefix, suffix, new File(tempFolderPath));
    }
  }

  protected Map<String, List<String>> buildResponseHeaders(Response response) {
    Map<String, List<String>> responseHeaders = new HashMap<>();
    for (Entry<String, List<Object>> entry : response.getHeaders().entrySet()) {
      List<Object> values = entry.getValue();
      List<String> headers = new ArrayList<>();
      for (Object o : values) {
        headers.add(String.valueOf(o));
      }
      responseHeaders.put(entry.getKey(), headers);
    }
    return responseHeaders;
  }

  /**
   * Update query and header parameters based on authentication settings.
   *
   * @param authNames The authentications to apply
   */
  protected void updateParamsForAuth(String[] authNames, Map<String, String> headerParams) throws ApiException {

    if (authNames == null && this.enforcedAuthenticationSchemes.isEmpty()) {
      return;
    }

    authNames = withEnforcedSecurityScheme(authNames);

    for (String authName : authNames) {
      Authentication auth = this.authentications.get(authName);
      if (auth == null) {
        throw new RuntimeException("Authentication undefined: " + authName);
      }
      auth.apply(headerParams);
    }
  }

  private String[] withEnforcedSecurityScheme(String[] authNames) {

    if (authNames == null) {
      authNames = new String[0];
    }

    return Stream.concat(this.enforcedAuthenticationSchemes.stream(), Arrays.stream(authNames)).toArray(String[]::new);
  }
}
