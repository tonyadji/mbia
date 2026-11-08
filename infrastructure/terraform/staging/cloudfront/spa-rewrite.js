// Viewer request of the frontend distribution (infrastructure/staging/README.md §Frontend on CloudFront):
// a URI whose last segment has no dot is a route of the SPA, served by index.html. Files keep their URI,
// so a missing asset stays an error. The application's paths hold no dot (UUIDs, years, base64url tokens).
function handler(event) {
  var request = event.request;
  var last = request.uri.substring(request.uri.lastIndexOf('/') + 1);
  if (last.indexOf('.') === -1) {
    request.uri = '/index.html';
  }
  return request;
}
