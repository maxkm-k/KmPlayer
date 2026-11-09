import 'dart:io';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';
import 'package:file_picker/file_picker.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  runApp(const KmPlayerApp());
}

class KmPlayerApp extends StatelessWidget {
  const KmPlayerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Km Player',
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark(useMaterial3: true),
      home: const VideoPlayerScreen(),
    );
  }
}

class VideoPlayerScreen extends StatefulWidget {
  const VideoPlayerScreen({super.key});

  @override
  State<VideoPlayerScreen> createState() => _VideoPlayerScreenState();
}

class _VideoPlayerScreenState extends State<VideoPlayerScreen> {
  VideoPlayerController? _controller;
  List> _playlist = [];
  String _currentTitle = 'No Video Playing';
  double _playbackSpeed = 1.0;

  @override
  void initState() {
    super.initState();
    _loadPlaylist();
  }

  Future<void> _loadPlaylist() async {
    final prefs = await SharedPreferences.getInstance();
    final String? data = prefs.getString('km_saved_playlist');
    if (data != null) {
      final List decoded = jsonDecode(data);
      setState(() {
        _playlist = decoded
            .map((e) => Map<String, String>.from(e as Map))
            .toList();
      });
    }
  }

  Future<void> _savePlaylist() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('km_saved_playlist', jsonEncode(_playlist));
  }

  Future<void> _pickVideo() async {
    FilePickerResult? result = await FilePicker.platform.pickFiles(
      type: FileType.video,
      allowMultiple: false,
    );

    if (result != null && result.files.single.path != null) {
      final path = result.files.single.path!;
      final name = result.files.single.name;

      _startPlayback(path, name);

      setState(() {
        _playlist.removeWhere((item) => item['path'] == path);
        _playlist.insert(0, {'name': name, 'path': path});
      });
      _savePlaylist();
    }
  }

  void _startPlayback(String path, String name) {
    _controller?.dispose();
    _controller = VideoPlayerController.file(File(path))
      ..initialize().then((_) {
        setState(() {
          _currentTitle = name;
          _controller!.setPlaybackSpeed(_playbackSpeed);
          _controller!.play();
        });
      });

    _controller!.addListener(() {
      if (mounted) setState(() {});
    });
  }

  String _formatDuration(Duration duration) {
    String twoDigits(int n) => n.toString().padLeft(2, '0');
    final minutes = twoDigits(duration.inMinutes.remainder(60));
    final seconds = twoDigits(duration.inSeconds.remainder(60));
    return '$minutes:$seconds';
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isInitialized = _controller != null && _controller!.value.isInitialized;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Km Player'),
        actions: [
          IconButton(
            icon: const Icon(Icons.video_file_outlined),
            tooltip: 'Open Video',
            onPressed: _pickVideo,
          ),
        ],
      ),
      body: Column(
        children: [
          // Video Playback Area
          Container(
            height: 240,
            width: double.infinity,
            color: Colors.black,
            child: isInitialized
                ? Center(
                    child: AspectRatio(
                      aspectRatio: _controller!.value.aspectRatio,
                      child: VideoPlayer(_controller!),
                    ),
                  )
                : const Center(
                    child: Text(
                      'Tap folder icon to open video',
                      style: TextStyle(color: Colors.white70),
                    ),
                  ),
          ),

          // Player Control Bar
          if (isInitialized) ...[
            VideoProgressIndicator(
              _controller!,
              allowScrubbing: true,
              colors: const VideoProgressColors(
                playedColor: Colors.blueAccent,
                bufferedColor: Colors.white24,
                backgroundColor: Colors.grey,
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12.0),
              child: Row(
                children: [
                  Text(
                    _formatDuration(_controller!.value.position),
                    style: const TextStyle(fontSize: 12),
                  ),
                  const Text(' / ', style: TextStyle(fontSize: 12)),
                  Text(
                    _formatDuration(_controller!.value.duration),
                    style: const TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                  const Spacer(),
                  IconButton(
                    icon: Icon(_controller!.value.isPlaying ? Icons.pause : Icons.play_arrow),
                    onPressed: () {
                      setState(() {
                        _controller!.value.isPlaying ? _controller!.pause() : _controller!.play();
                      });
                    },
                  ),
                  PopupMenuButton<double>(
                    tooltip: 'Playback Speed',
                    icon: const Icon(Icons.speed),
                    onSelected: (speed) {
                      setState(() {
                        _playbackSpeed = speed;
                        _controller?.setPlaybackSpeed(speed);
                      });
                    },
                    itemBuilder: (context) => [
                      0.5, 0.75, 1.0, 1.25, 1.5, 2.0
                    ].map((s) => PopupMenuItem(value: s, child: Text('${s}x'))).toList(),
                  ),
                ],
              ),
            ),
          ],

          // Title & Playlist Header
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Text(
                    _currentTitle,
                    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                TextButton(
                  onPressed: () {
                    setState(() {
                      _playlist.clear();
                    });
                    _savePlaylist();
                  },
                  child: const Text('Clear History', style: TextStyle(color: Colors.redAccent)),
                ),
              ],
            ),
          ),
          const Divider(),

          // Persistent Playlist History List
          Expanded(
            child: _playlist.isEmpty
                ? const Center(
                    child: Text('No videos played yet', style: TextStyle(color: Colors.grey)),
                  )
                : ListView.builder(
                    itemCount: _playlist.length,
                    itemBuilder: (context, index) {
                      final item = _playlist[index];
                      return ListTile(
                        leading: const Icon(Icons.movie, color: Colors.blueAccent),
                        title: Text(item['name'] ?? 'Unknown', maxLines: 1),
                        subtitle: Text(item['path'] ?? '', maxLines: 1),
                        onTap: () {
                          if (item['path'] != null) {
                            _startPlayback(item['path']!, item['name'] ?? 'Video');
                          }
                        },
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}
