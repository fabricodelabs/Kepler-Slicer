#include <draco/compression/decode.h>

// This checks compilation and linking, not Android runtime or slicing.
extern "C" int kepler_draco_probe(const char *data, unsigned int length)
{
    draco::DecoderBuffer buffer;
    buffer.Init(data, length);
    return draco::Decoder::GetEncodedGeometryType(&buffer).ok() ? 1 : 0;
}
