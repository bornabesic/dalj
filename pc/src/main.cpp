
#include <cstdlib>
#include <iostream>
#include <netinet/in.h>
#include <signal.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <vector>

#include <opencv2/core/core.hpp>
#include <opencv2/highgui/highgui.hpp>

using std::cout;
using std::vector;

constexpr int PORT = 10101;
constexpr int MAX_PACKET_SIZE = 62 * 1000;

void sigint_handler(int signum) { exit(EXIT_SUCCESS); }

int get_recv_buffer_size(int socket_descriptor) {
    int size;
    unsigned int int_size = sizeof(size);
    getsockopt(socket_descriptor, SOL_SOCKET, SO_RCVBUF, (void *)&size,
               &int_size);
    return size;
}

int set_recv_buffer_size(int socket_descriptor, int size) {
    return setsockopt(socket_descriptor, SOL_SOCKET, SO_RCVBUF, &size,
                      sizeof(int));
}

int main(void) {
    int socket_descriptor = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
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

    set_recv_buffer_size(socket_descriptor, MAX_PACKET_SIZE * 60);
    cout << "Receive buffer size: " << get_recv_buffer_size(socket_descriptor)
         << " bytes"
         << "\n";

    signal(SIGINT, sigint_handler);

    vector<uint8_t> buffer(MAX_PACKET_SIZE);
    cv::Mat image;
    while (true) {
        recv(socket_descriptor, &buffer[0], MAX_PACKET_SIZE, MSG_WAITALL);
        image = cv::imdecode(buffer, cv::IMREAD_UNCHANGED);
        cv::imshow("Image", image);
        cv::waitKey(1);
    }

    return EXIT_SUCCESS;
}