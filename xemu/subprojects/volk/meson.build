project('volk', 'c')

volk_compile_args = ['-DVK_NO_PROTOTYPES', '-DVOLK_STATIC_DEFINES=VK_NO_PROTOTYPES']
if host_machine.system() == 'windows'
  volk_compile_args += ['-DVK_USE_PLATFORM_WIN32_KHR=1']
elif host_machine.system() == 'android'
  volk_compile_args += ['-DVK_USE_PLATFORM_ANDROID_KHR=1']
endif

libvolk = static_library('volk',
                         files('volk.c'),
                         c_args: volk_compile_args,
                         pic: true,
                         include_directories: include_directories('.'))

volk_dep = declare_dependency(compile_args: ['-DVK_NO_PROTOTYPES'],
                              include_directories: include_directories('.'),
                              link_with: libvolk)
