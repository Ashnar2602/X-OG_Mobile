subdir('interp')

dsp_sources = files(['dsp.c', 'dsp_c.c', 'dsp_dma.c'])
dsp_deps = []
dsp_c_args = []

if host_os == 'android'
  dsp_c_args += ['-DXEMU_DISABLE_DSP_JIT=1']
else
  dsp56300_emu_ffi = dependency('dsp56300-emu-ffi', fallback: ['dsp56300', 'dsp56300_dep'])
  dsp_sources += files('dsp_jit.c')
  dsp_deps += [dsp56300_emu_ffi]
endif

libdsp = static_library(
  'dsp',
  dsp_sources + genh,
  dependencies: dsp_deps,
  c_args: dsp_c_args,
)

dsp = declare_dependency(
  objects: [
    libdsp.extract_all_objects(recursive: false),
    libdsp_cpu_interp.extract_all_objects(recursive: false),
  ],
  dependencies: dsp_deps,
)

mcpx_ss.add(dsp, files('gp_ep.c'))
