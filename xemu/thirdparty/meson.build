if vulkan.found()

debug_vma = false

vma_defns = [
    '-DVMA_STATIC_VULKAN_FUNCTIONS=0',
    '-DVMA_DYNAMIC_VULKAN_FUNCTIONS=0',
    ]

if debug_vma
    vma_defns += [
        '-DVMA_DEBUG_MARGIN=16',
        '-DVMA_DEBUG_DETECT_CORRUPTION=1',
        '-DVMA_DEBUG_MIN_BUFFER_IMAGE_GRANULARITY=256',
    ]
endif

vma_opts = cmake.subproject_options()
vma_opts.add_cmake_defines({'CMAKE_POSITION_INDEPENDENT_CODE': 'ON'})
vma_subproj = cmake.subproject('VulkanMemoryAllocator', options: vma_opts)
libvma = static_library('vma',
                        sources: 'vma.cc',
                        cpp_args: vma_defns,
                        pic: true,
                        include_directories: vma_subproj.include_directories('VulkanMemoryAllocator'),
                        dependencies: [vulkan, volk])
vma = declare_dependency(include_directories: vma_subproj.include_directories('VulkanMemoryAllocator'),
                         link_with: libvma,
                         dependencies: vulkan)

endif

subdir('nvapi')
