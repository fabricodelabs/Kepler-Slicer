#include <opencv2/imgcodecs.hpp>
#include <assimp/Importer.hpp>
#include <vector>
extern "C" int check_decode(const unsigned char* data, unsigned int size) {
    std::vector<unsigned char> bytes(data, data + size);
    cv::Mat image = cv::imdecode(bytes, cv::IMREAD_UNCHANGED);
    Assimp::Importer importer;
    return image.rows + (importer.ReadFileFromMemory(data,size,0) != nullptr ? 1 : 0);
}
