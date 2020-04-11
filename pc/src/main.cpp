
#include <cstdlib>
#include <iostream>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <vector>

#include <opencv2/imgcodecs.hpp>
#include <opencv2/highgui/highgui.hpp>

using std::cout;
using std::vector;

constexpr int PORT = 10101;
constexpr int PACKET_SIZE = 24 * 1000;

int main(void) {
    int socket_descriptor = socket(AF_INET, SOCK_DGRAM, 0);
    if (socket_descriptor == -1) {
        cout << "Cannot create socket!"
             << "\n";
        return EXIT_FAILURE;
    }

    struct sockaddr_in address = {0};
    address.sin_family = AF_INET; // IPv4
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(PORT);

    if (bind(socket_descriptor, (const struct sockaddr *)&address,
             sizeof(address)) < 0) {
        cout << "Bind failed."
             << "\n";
        return EXIT_FAILURE;
    }

    uint8_t buffer[PACKET_SIZE];
    int n_bytes = 0;
    vector<uint8_t> buffer_;
    cv::Mat image;
    while (true) {
        n_bytes = recv(socket_descriptor, buffer, PACKET_SIZE, MSG_WAITALL);
        buffer_ = vector<uint8_t>(buffer, buffer + PACKET_SIZE);
        image = cv::imdecode(buffer_, 1);
        cv::imshow("Image", image);
        cv::waitKey(0);
        cout << image.cols << " " << image.rows << "\n";
    }

    return EXIT_SUCCESS;
}