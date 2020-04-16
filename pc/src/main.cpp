
#include <chrono>
#include <cstdlib>
#include <iostream>
#include <netinet/in.h>
#include <signal.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <vector>

#include <opencv2/core/core.hpp>
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/imgproc/imgproc.hpp>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/avutil.h>
#include <libswscale/swscale.h>
}

using std::cout;
using std::vector;

constexpr int PORT = 10101;
constexpr int MAX_PACKET_SIZE = 62 * 1000;
constexpr long MAX_DELAY_TIME_MS = 1000;
// constexpr int SIZE_MULTIPLIER = 2;

static int socket_descriptor;

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

inline cv::Mat avframe_to_cvmat(AVFrame *frame) {
    static AVFrame dst;
    memset(&dst, 0, sizeof(dst));

    int w = frame->width, h = frame->height;
    cv::Mat m = cv::Mat(h, w, CV_8UC3);

    dst.data[0] = (uint8_t *)m.data;
    avpicture_fill((AVPicture *)&dst, dst.data[0], AV_PIX_FMT_BGR24, w, h);

    struct SwsContext *convert_ctx = NULL;
    enum AVPixelFormat src_pixfmt = AV_PIX_FMT_YUV420P;
    enum AVPixelFormat dst_pixfmt = AV_PIX_FMT_BGR24;
    convert_ctx = sws_getContext(w, h, src_pixfmt, w, h, dst_pixfmt, 0, NULL,
                                 NULL, NULL);

    sws_scale(convert_ctx, frame->data, frame->linesize, 0, h, dst.data,
              dst.linesize);

    sws_freeContext(convert_ctx);

    return m;
}

int main(void) {
    avcodec_register_all();
    av_register_all();
    avformat_network_init();

    AVFormatContext *context = avformat_alloc_context();
    if (context == nullptr) {
        cout << "Cannot allocate avformat context."
             << "\n";
    }

    if (avformat_open_input(&context, "udp://localhost:10101", NULL, NULL) !=
        0) {
        cout << "Couldn't open input stream."
             << "\n";
        return EXIT_FAILURE;
    } else {
        cout << "Stream opened."
             << "\n";
    }

    AVPacket *packet = (AVPacket *)av_malloc(sizeof(AVPacket));

    int videoIndex = -1;
    for (int i = 0; i < context->nb_streams; i++)
        if (context->streams[i]->codec->codec_type == AVMEDIA_TYPE_VIDEO) {
            videoIndex = i;
            break;
        }

    if (videoIndex == -1) {
        printf("Didn't find a video stream.\n");
        return EXIT_FAILURE;
    }

    AVCodecContext *codec_context = context->streams[videoIndex]->codec;

    AVCodec *codec = avcodec_find_decoder(codec_context->codec_id);
    if (codec == nullptr) {
        cout << "Cannot find H264 codec."
             << "\n";
        return EXIT_FAILURE;
    }

    if (avcodec_open2(codec_context, codec, NULL) < 0) {
        cout << "Could not open codec."
             << "\n";
        return EXIT_FAILURE;
    }

    AVFrame *frame = av_frame_alloc();

    int got_image;
    while (av_read_frame(context, packet) >= 0) {
        avcodec_decode_video2(codec_context, frame, &got_image, packet);
        if (!got_image)
            continue;

        // AV_PIX_FMT_YUV420P
        cv::Mat image = avframe_to_cvmat(frame);
        cv::imshow("Image", image);
        cv::waitKey(1);

        av_free_packet(packet);
    }

    return EXIT_SUCCESS;
}