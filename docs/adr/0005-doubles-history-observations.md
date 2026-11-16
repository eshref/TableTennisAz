# Build doubles histories from on-device ranking observations

Website inspection has not established a historical doubles-data feed, so the
app will save published pair points and positions on the device when doubles
rankings refresh and build rating and ranking histories from those observations.
This preserves direct website access without operating a history service, at
the cost of a device-specific history that starts at the first saved observation
and has gaps between refreshes. Earlier values must not be inferred from match
results or individual-player history endpoints.
