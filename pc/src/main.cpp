
#include <cstdlib>
#include <iostream>
#include <sys/socket.h>

using std::cout;

int main(void) {
    int socket_descriptor = socket(AF_INET, SOCK_DGRAM, 0);
    if (socket_descriptor == -1) {
        cout << "Cannot create socket!"
             << "\n";
        return EXIT_FAILURE;
    }
    return EXIT_SUCCESS;
}